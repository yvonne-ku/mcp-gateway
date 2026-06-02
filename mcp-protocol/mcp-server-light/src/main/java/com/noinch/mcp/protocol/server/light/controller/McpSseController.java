package com.noinch.mcp.protocol.server.light.controller;

import com.noinch.mcp.protocol.core.jsonrpc.*;
import com.noinch.mcp.protocol.core.mcp.model.CallToolResult;
import com.noinch.mcp.protocol.core.mcp.registry.McpToolRegistry;
import com.noinch.mcp.protocol.server.light.service.LightService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP SSE 控制器
 * 手动实现 MCP 2025-11-25 协议，无需任何 Spring AI 依赖
 *
 * 协议流程：
 * 1. 客户端 GET /sse → 建立 SSE 连接，服务器下发 sessionId 和消息端点地址
 * 2. 客户端 POST /messages?sessionId=xxx → 发送 JSON-RPC 请求
 * 3. 服务器处理请求，通过 SSE 回推响应
 */
@Slf4j
@RestController
public class McpSseController {

    private final McpToolRegistry toolRegistry = new McpToolRegistry();
    private final Map<String, SseEmitter> sessions = new ConcurrentHashMap<>();
    private final LightService lightService;

    public McpSseController(LightService lightService) {
        this.lightService = lightService;
    }

    @PostConstruct
    public void init() {
        toolRegistry.registerTools(lightService, LightService.class);
        log.info("Light server registered {} tools: {}", toolRegistry.size(), toolRegistry.getToolNames());
    }

    /**
     * SSE 连接端点
     */
    @GetMapping(value = "/sse", produces = "text/event-stream")
    public SseEmitter connect() {
        String sessionId = UUID.randomUUID().toString();
        log.info("New SSE connection, sessionId: {}", sessionId);

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        sessions.put(sessionId, emitter);

        try {
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data("/messages?sessionId=" + sessionId));
        } catch (IOException e) {
            log.error("Failed to send endpoint event", e);
            sessions.remove(sessionId);
            emitter.completeWithError(e);
        }

        emitter.onCompletion(() -> {
            log.info("SSE connection completed: {}", sessionId);
            sessions.remove(sessionId);
        });
        emitter.onTimeout(() -> {
            log.info("SSE connection timeout: {}", sessionId);
            sessions.remove(sessionId);
        });
        emitter.onError(e -> {
            log.error("SSE connection error: {}", sessionId, e);
            sessions.remove(sessionId);
        });

        return emitter;
    }

    /**
     * 消息端点
     */
    @PostMapping(value = "/messages", consumes = "application/json")
    public void handleMessage(@RequestParam String sessionId, @RequestBody String body) {
        log.info("Received message from session {}: {}", sessionId, body);

        SseEmitter emitter = sessions.get(sessionId);
        if (emitter == null) {
            log.warn("No session found: {}", sessionId);
            return;
        }

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
        log.info("Handling request: method={}, id={}", request.getMethod(), request.getId());

        String response;

        switch (request.getMethod()) {
            case "initialize" -> {
                log.info("Client initialize, session: {}", sessionId);
                Map<String, Object> result = Map.of(
                        "protocolVersion", "2025-11-25",
                        "capabilities", Map.of("tools", Map.of()),
                        "serverInfo", Map.of("name", "mcp-server-light", "version", "1.0.0")
                );
                response = JsonRpcCodec.successResponse(request.getId(), result);
            }
            case "tools/list" -> {
                Map<String, Object> result = Map.of("tools", toolRegistry.listToolDefinitions());
                response = JsonRpcCodec.successResponse(request.getId(), result);
            }
            case "tools/call" -> {
                Map<String, Object> params = request.getParams();
                CallToolResult result = executeTool(params);
                response = JsonRpcCodec.successResponse(request.getId(), result);
            }
            default -> {
                response = JsonRpcCodec.errorResponse(request.getId(),
                        JsonRpcError.methodNotFound("Unknown method: " + request.getMethod()));
            }
        }

        sendSseEvent(emitter, response);
    }

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
        String name = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

        if (name == null) {
            return CallToolResult.error("Missing tool name");
        }

        try {
            var entry = toolRegistry.getTool(name);
            if (entry.isEmpty()) {
                return CallToolResult.error("Tool not found: " + name);
            }
            Object result = entry.get().invoke(arguments);
            return CallToolResult.success(result.toString());
        } catch (Exception e) {
            log.error("Tool execution failed: {}", name, e);
            return CallToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    private void sendSseEvent(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(data));
        } catch (IOException e) {
            log.error("Failed to send SSE event", e);
            emitter.completeWithError(e);
        }
    }
}
