package com.noinch.mcp.server.starter;

import com.noinch.mcp.protocol.core.jsonrpc.JsonRpcCodec;
import com.noinch.mcp.protocol.core.jsonrpc.JsonRpcError;
import com.noinch.mcp.protocol.core.jsonrpc.JsonRpcNotification;
import com.noinch.mcp.protocol.core.jsonrpc.JsonRpcRequest;
import com.noinch.mcp.protocol.core.mcp.model.CallToolResult;
import com.noinch.mcp.protocol.core.mcp.registry.McpToolRegistry;
import com.noinch.mcp.server.starter.config.McpServerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用 MCP SSE 控制器
 *<p>
 * 由 McpSseAutoConfiguration 自动注册，应用层只需要：
 * 1. 编写带 {@code @McpTool} 方法的 Service
 * 2. 配置 {@code mcp.server.name} 和 {@code mcp.server.version}
 *<p>
 * 协议流程：
 * 一个伪双工通信，SSE 通道 + POST 通道（全双工要求双向数据通过一个连接一个通道）
 * 1. 客户端 GET /sse → 建立 SSE 连接，服务端下发 sessionId 和消息端点地址，SSE 协议支持服务端向客户端的单向流式、分段、多次的推送
 * 2. 客户端 POST /messages?sessionId=xxx → 发送 JSON-RPC 请求，来调用工具的回调方法
 */
@Slf4j
@RestController
public class McpSseController {

    private final McpToolRegistry toolRegistry;
    private final McpServerProperties properties;
    private final Map<String, SseEmitter> sessions = new ConcurrentHashMap<>();

    public McpSseController(McpToolRegistry toolRegistry, McpServerProperties properties) {
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        log.info("MCP SSE controller initialized with {} tools: {}",
                toolRegistry.size(), toolRegistry.getToolNames());
    }

    /**
     * 客户端 GET /sse → 建立 SSE 连接
     * 注：
     * 1. produces = "text/event-stream" 用于规定通过 SSE 回推事件的格式返回响应。不加这个，浏览器不会把它当成长连接，就无法实现实时推送
     * 2. 为了管理多客户端连接，使用 Map 存储 sessionId 与 emitter，方便进行后续操作
     * 3. 服务端需要下发 sessionId 和消息端点地址
     * 4. 服务端需要设置对于此 SSE 连接 完成、超时、错误 的处理逻辑
     */
    @GetMapping(value = "/sse", produces = "text/event-stream")
    public SseEmitter connect() {

        String sessionId = UUID.randomUUID().toString();
        log.info("New SSE connection, sessionId: {}", sessionId);

        // 把这个 SSE 连接的 emitter 保存起来，方便后续操作
        // 用 Map 因为会有很多客户端来请求建立 SSE 连接，来获得工具
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        sessions.put(sessionId, emitter);

        // 服务端下发 sessionId 和消息端点地址给客户端
        try {
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data("/messages?sessionId=" + sessionId));
        } catch (IOException e) {
            log.error("Error sending endpoint event to client, sse sessionId is: {}", sessionId, e);
            sessions.remove(sessionId);
            emitter.completeWithError(e);
        }

        // 设置 SSE 连接 完成、超时、错误 的处理逻辑
        emitter.onCompletion(() -> {
            log.info("SSE connection completed, sessionId: {}", sessionId);
            sessions.remove(sessionId);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE connection timeout, sessionId: {}", sessionId);
            sessions.remove(sessionId);
        });
        emitter.onError((e) -> {
            log.error("SSE connection error, sessionId: {}", sessionId, e);
            sessions.remove(sessionId);
        });

        return emitter;
    }

    /**
     * 客户端 POST /messages?sessionId=xxx → 发送 JSON-RPC 请求
     * @param sessionId SSE 连接 ID
     * @param body 请求体
     * 注：
     * 1. consumes = "application/json" 用于规定客户端的请求格式为 JSON 格式
     */
    @PostMapping(value = "/messages", consumes = "application/json")
    public void handleMessage(@RequestParam String sessionId, @RequestBody String body) {

        log.info("Received message from session {}: {}", sessionId, body);

        // 取 sessionId 对应的 emitter
        SseEmitter emitter = sessions.get(sessionId);
        if (emitter == null) {
            log.warn("No session found: {}", sessionId);
            return;
        }

        // 解析 body，分为 notification 和 request 的处理逻辑
        try {
            Object parsed = JsonRpcCodec.parse(body);
            if (parsed instanceof JsonRpcRequest request) {
                handleRequest(sessionId, emitter, request);
            } else if (parsed instanceof JsonRpcNotification notification) {
                handleNotification(sessionId, notification);
            }
        } catch (Exception e) {
            log.error("Failed to handle message", e);
            sendSseEvent(emitter, JsonRpcCodec.errorResponse(null,
                    JsonRpcError.parseError(e.getMessage())));
        }
    }

    private void handleRequest(String sessionId, SseEmitter emitter, JsonRpcRequest request) {
        log.info("Handling request from client, sessionId: {}, requestId: {}", sessionId, request.getId());

        String response;

        switch(request.getMethod()){
            case "initialize" -> {
                // 初始化连接，向客户端返回服务端信息
                log.info("Client initialize tools, sessionId: {}", sessionId);
                Map<String, Object> result = Map.of(
                        "protocolVersion", properties.getProtocolVersion(),
                        "capabilities", properties.getCapabilities(),
                        "serverInfo", Map.of("name", properties.getName(), "version", properties.getVersion())
                );
                response = JsonRpcCodec.successResponse(request.getId(), result);
            }
            case "tools/list" -> {
                // 向客户但返回服务端工具信息
                Map<String, Object> result = Map.of("tools", toolRegistry.listToolDefinitions());
                response = JsonRpcCodec.successResponse(request.getId(), result);
            }
            case "tools/call" -> {
                // 向客户端返回工具调用结果
                Map<String, Object> params = request.getParams();
                CallToolResult result = executeTool(params);
                response = JsonRpcCodec.successResponse(request.getId(), result);
            }
            default -> {
                // 未知方法，返回错误响应
                response = JsonRpcCodec.errorResponse(request.getId(),
                        JsonRpcError.methodNotFound("Unknown method: " + request.getMethod()));
            }
        }

        sendSseEvent(emitter, response);
    }

    /**
     * 客户端通知服务端已经完成准备
     * @param sessionId SSE ID
     * @param notification 通知
     */
    private void handleNotification(String sessionId, JsonRpcNotification notification) {
        log.info("Handling notification: method={}", notification.getMethod());
        if ("notifications/initialized".equals(notification.getMethod())) {
            log.info("Client {} initialized successfully", sessionId);
        } else {
            log.warn("Unknown notification method: {}", notification.getMethod());
        }
    }

    @SuppressWarnings("unchecked")
    private CallToolResult executeTool(Map<String, Object> params) {
        if (params == null) {
            return CallToolResult.error("Missing params");
        }

        // 调用工具的名称，参数
        String name = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

        if (name == null) {
            return CallToolResult.error("Missing tool name");
        }

        // 调用工具
        try {
            var tool = toolRegistry.getTool(name);
            if (tool.isEmpty()) {
                return CallToolResult.error("Tool not found: " + name);
            }
            Object result = tool.get().invoke(arguments);
            return CallToolResult.success(result.toString());
        } catch (Exception e) {
            log.error("Tool execution failed: {}", name, e);
            return CallToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    private void sendSseEvent(SseEmitter emitter, String response) {
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(response));
        } catch (IOException e) {
            log.error("Failed to send SSE event", e);
            emitter.completeWithError(e);
        }
    }
}
