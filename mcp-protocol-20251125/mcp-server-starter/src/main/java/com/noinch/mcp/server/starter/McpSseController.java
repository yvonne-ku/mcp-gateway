package com.noinch.mcp.server.starter;

import com.noinch.mcp.protocol.core.jsonrpc.JsonRpcCodec;
import com.noinch.mcp.protocol.core.jsonrpc.JsonRpcError;
import com.noinch.mcp.protocol.core.jsonrpc.JsonRpcNotification;
import com.noinch.mcp.protocol.core.jsonrpc.JsonRpcRequest;
import com.noinch.mcp.protocol.core.mcp.McpConstants;
import com.noinch.mcp.protocol.core.mcp.model.CallToolResult;
import com.noinch.mcp.protocol.core.mcp.registry.McpToolRegistry;
import com.noinch.mcp.server.starter.config.McpServerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 通用 MCP SSE 控制器（2025-11-25 稳定版）
 *
 * <p>Streamable HTTP 传输：
 * <ul>
 *   <li>GET /mcp → 建立 SSE 连接</li>
 *   <li>POST /mcp → 发送 JSON-RPC 请求</li>
 *   <li>DELETE /mcp → 终止会话</li>
 * </ul>
 */
@Slf4j
@RestController
public class McpSseController {

    private final McpToolRegistry toolRegistry;
    private final McpServerProperties properties;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public McpSseController(McpToolRegistry toolRegistry, McpServerProperties properties) {
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        log.info("MCP SSE controller initialized with {} tools: {}",
                toolRegistry.size(), toolRegistry.getToolNames());
    }

    /**
     * 客户端 GET /mcp → 建立 SSE 连接
     * <p>响应头返回 {@code Mcp-Session-Id}，符合 Streamable HTTP 规范。
     */
    @GetMapping(value = McpConstants.MCP_ENDPOINT, produces = "text/event-stream")
    public ResponseEntity<SseEmitter> connect(
            @RequestHeader(value = "Origin", required = false) String origin) {

        // 1) 校验 Origin
        ResponseEntity<Void> originCheck = checkOrigin(origin);
        if (originCheck != null) {
            return ResponseEntity.status(403).build();
        }

        // 2) 初始化会话状态：生成会话 ID，创建会话对象，初始化会话状态
        String sessionId = UUID.randomUUID().toString();
        log.info("New SSE connection, sessionId: {}", sessionId);

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        SessionState state = new SessionState(sessionId, emitter);
        sessions.put(sessionId, state);

        // 3) SSE 告知客户端端点地址
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(state.eventId.incrementAndGet()))
                    .name(McpConstants.SSE_EVENT_ENDPOINT)
                    .data(McpConstants.MCP_ENDPOINT));
        } catch (IOException e) {
            log.error("Error sending {} event, sessionId: {}", McpConstants.SSE_EVENT_ENDPOINT, sessionId, e);
            sessions.remove(sessionId);
            emitter.completeWithError(e);
        }

        // 4) 定义特殊事件处理逻辑
        emitter.onCompletion(() -> {
            log.info("SSE connection completed, sessionId: {}", sessionId);
            sessions.remove(sessionId);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE connection timeout, sessionId: {}", sessionId);
            sessions.remove(sessionId);
        });
        emitter.onError(e -> {
            log.error("SSE connection error, sessionId: {}", sessionId, e);
            sessions.remove(sessionId);
        });

        // 5) Mcp-Session-Id 通过响应头传递（规范 MUST）
        return ResponseEntity.ok()
                .header(McpConstants.HEADER_SESSION_ID, sessionId)
                .body(emitter);
    }

    /**
     * 客户端 POST /mcp → 发送 JSON-RPC 请求
     * <p>需携带 {@code Mcp-Session-Id} 请求头，缺失返回 400，无效返回 404。
     */
    @PostMapping(value = McpConstants.MCP_ENDPOINT, consumes = "application/json")
    public ResponseEntity<Void> handleMessage(
            @RequestHeader(value = McpConstants.HEADER_SESSION_ID, required = false) String sessionId,
            @RequestHeader(value = "Origin", required = false) String origin,
            @RequestBody String body) {

        // 1) 校验 Origin
        ResponseEntity<Void> originCheck = checkOrigin(origin);
        if (originCheck != null) {
            return originCheck;
        }

        // 2) 校验 Mcp-Session-Id 头是否缺失
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Missing Mcp-Session-Id header");
            return ResponseEntity.badRequest().build();
        }

        // 3) 校验会话是否存在
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            log.warn("Session not found: {}", sessionId);
            return ResponseEntity.notFound().build();
        }
        SseEmitter emitter = state.emitter;

        // 4) 解析并处理 JSON-RPC 消息
        try {
            Object parsed = JsonRpcCodec.parse(body);
            // 4.1 解析为 JsonRpcRequest
            // 4.2 解析为 JsonRpcNotification
            if (parsed instanceof JsonRpcRequest request) {
                handleRequest(state, request);
            } else if (parsed instanceof JsonRpcNotification notification) {
                handleNotification(sessionId, notification);
            }
        } catch (Exception e) {
            log.error("Failed to handle message from session {}", sessionId, e);
            sendSseEvent(emitter, state,
                    JsonRpcCodec.errorResponse(null, JsonRpcError.parseError(e.getMessage())));
        }
        return ResponseEntity.ok().build();
    }

    /**
     * 客户端 DELETE /mcp → 终止会话
     * <p>需携带 {@code Mcp-Session-Id} 请求头，缺失返回 400，无效返回 404。
     */
    @DeleteMapping(McpConstants.MCP_ENDPOINT)
    public ResponseEntity<Void> deleteSession(
            @RequestHeader(value = McpConstants.HEADER_SESSION_ID, required = false) String sessionId,
            @RequestHeader(value = "Origin", required = false) String origin) {

        // 1) 校验 Origin
        ResponseEntity<Void> originCheck = checkOrigin(origin);
        if (originCheck != null) {
            return originCheck;
        }

        // 2) 校验 Mcp-Session-Id 头是否缺失
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Missing Mcp-Session-Id header on DELETE");
            return ResponseEntity.badRequest().build();
        }

        // 3) 清理 session 资源
        SessionState state = sessions.remove(sessionId);
        if (state == null) {
            log.warn("Session not found for DELETE: {}", sessionId);
            return ResponseEntity.notFound().build();
        }

        // 4) 清理 SseEmitter 资源
        state.emitter.complete();
        log.info("Session terminated by DELETE: {}", sessionId);
        return ResponseEntity.ok().build();
    }

    /**
     * 校验 Origin 头是否在白名单内
     *
     * @return null 表示通过，非 null 为 403 响应
     */
    private ResponseEntity<Void> checkOrigin(String origin) {
        List<String> allowed = properties.getAllowedOrigins();
        if (allowed.isEmpty()) {
            return null; // 未配置白名单，不校验
        }
        if (origin == null || origin.isBlank()) {
            return null; // 无 Origin 头（非浏览器请求），放行
        }
        if (allowed.contains(origin)) {
            return null; // 在白名单内
        }
        log.warn("Origin '{}' not allowed", origin);
        return ResponseEntity.status(403).build();
    }

    private void handleRequest(SessionState state, JsonRpcRequest request) {
        String sessionId = state.sessionId;
        SseEmitter emitter = state.emitter;
        log.info("Handling request, sessionId: {}, method: {}, id: {}",
                sessionId, request.getMethod(), request.getId());

        // 拒绝除 ping 外的所有请求，在客户端 initialized 前发送
        if (!McpConstants.METHOD_PING.equals(request.getMethod()) && !state.initialized) {
            log.warn("Request '{}' before initialized, sessionId: {}", request.getMethod(), sessionId);
            sendSseEvent(emitter, state,
                    JsonRpcCodec.errorResponse(request.getId(),
                            JsonRpcError.invalidRequest("Not initialized")));
            return;
        }

        // 处理合格请求
        String response;

        switch (request.getMethod()) {
            case McpConstants.METHOD_PING -> {
                response = JsonRpcCodec.successResponse(request.getId(), Map.of());
            }
            case McpConstants.METHOD_INITIALIZE -> {
                log.info("Client initialize, sessionId: {}", sessionId);
                Map<String, Object> result = Map.of(
                        "protocolVersion", properties.getProtocolVersion(),
                        "capabilities", properties.getCapabilities(),
                        "serverInfo", properties.toServerInfo()
                );
                response = JsonRpcCodec.successResponse(request.getId(), result);
            }
            case McpConstants.METHOD_TOOLS_LIST -> {
                Map<String, Object> result = Map.of("tools", toolRegistry.listToolDefinitions());
                response = JsonRpcCodec.successResponse(request.getId(), result);
            }
            case McpConstants.METHOD_TOOLS_CALL -> {
                Map<String, Object> params = request.getParams();
                CallToolResult result = executeTool(params);
                response = JsonRpcCodec.successResponse(request.getId(), result);
            }
            default -> {
                response = JsonRpcCodec.errorResponse(request.getId(),
                        JsonRpcError.methodNotFound("Unknown method: " + request.getMethod()));
            }
        }

        sendSseEvent(emitter, state, response);
    }

    private void handleNotification(String sessionId, JsonRpcNotification notification) {
        log.info("Handling notification: method={}", notification.getMethod());
        if (McpConstants.METHOD_INITIALIZED.equals(notification.getMethod())) {
            SessionState state = sessions.get(sessionId);
            if (state != null) {
                state.initialized = true;
            }
            log.info("Session {} initialized", sessionId);
        } else {
            log.warn("Unknown notification method: {}", notification.getMethod());
        }
    }

    @SuppressWarnings("unchecked")
    private CallToolResult executeTool(Map<String, Object> params) {
        if (params == null) {
            return CallToolResult.error("Missing params");
        }

        String name = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

        if (name == null) {
            return CallToolResult.error("Missing tool name");
        }

        var tool = toolRegistry.getTool(name);
        if (tool.isEmpty()) {
            return CallToolResult.error("Tool not found: " + name);
        }

        try {
            Object result = tool.get().invoke(arguments);
            return CallToolResult.success(result.toString());
        } catch (Exception e) {
            log.error("Tool execution failed: {}", name, e);
            return CallToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    private void sendSseEvent(SseEmitter emitter, SessionState state, String data) {
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(state.eventId.incrementAndGet()))
                    .name(McpConstants.SSE_EVENT_MESSAGE)
                    .data(data));
        } catch (IOException e) {
            log.error("Failed to send SSE event, sessionId: {}", state.sessionId, e);
            emitter.completeWithError(e);
        }
    }

    /**
     * 会话状态：包装 emitter + initialized 标记 + 事件 ID 计数器
     */
    private static class SessionState {
        final String sessionId;
        final SseEmitter emitter;
        final AtomicLong eventId = new AtomicLong(0);
        volatile boolean initialized = false;

        SessionState(String sessionId, SseEmitter emitter) {
            this.sessionId = sessionId;
            this.emitter = emitter;
        }
    }
}
