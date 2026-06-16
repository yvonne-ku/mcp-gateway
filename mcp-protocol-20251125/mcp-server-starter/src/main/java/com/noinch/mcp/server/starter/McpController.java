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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

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
public class McpController {

    private final McpToolRegistry toolRegistry;
    private final McpServerProperties properties;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public McpController(McpToolRegistry toolRegistry, McpServerProperties properties) {
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        log.info("MCP SSE controller initialized with {} tools: {}",
                toolRegistry.size(), toolRegistry.getToolNames());
    }

    /**
     * GET /mcp – 建立 SSE 连接
     */
    @GetMapping(value = McpConstants.MCP_ENDPOINT, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<ServerSentEvent<String>>>> connect(
            @RequestHeader(value = "Origin", required = false) String origin) {

        // 1) 校验 Origin 头
        if (isOriginForbidden(origin)) {
            return Mono.just(ResponseEntity.status(403).build());
        }

        // 2) 初始化会话状态：生成会话 ID，创建会话对象，初始化会话状态
        String sessionId = UUID.randomUUID().toString();
        log.info("New SSE connection, sessionId: {}", sessionId);
        SessionState state = new SessionState(sessionId);
        sessions.put(sessionId, state);

        // 3) SSE 告知客户端端点地址，通过 replay() 缓存起来，直到被订阅才发送事件
        sendEndpointEvent(state);

        // 4) 当客户端主动断开或流结束时自动清理
        Flux<ServerSentEvent<String>> sseFlux = state.flux
                .doOnCancel(() -> cleanupSession(sessionId, "cancelled"))
                .doOnComplete(() -> cleanupSession(sessionId, "completed"))
                .doOnError(e -> cleanupSession(sessionId, "error: " + e.getMessage()));

        // 5) 返回和 sink 绑定的流，客户端收到后可以订阅
        return Mono.just(ResponseEntity.ok()
                .header(McpConstants.HEADER_SESSION_ID, sessionId)
                .body(sseFlux));
    }

    /**
     * POST /mcp – 处理 JSON-RPC 请求 / 通知
     */
    @PostMapping(value = McpConstants.MCP_ENDPOINT, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> handleMessage(
            @RequestHeader(value = McpConstants.HEADER_SESSION_ID, required = false) String sessionId,
            @RequestHeader(value = "Accept", defaultValue = "") String accept,
            @RequestHeader(value = "Origin", required = false) String origin,
            @RequestBody String body) {

        // 1) 校验 Origin 头
        if (isOriginForbidden(origin)) {
            return Mono.just(ResponseEntity.status(403).build());
        }

        // 2) 校验 Mcp-Session-Id 头是否缺失
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Missing Mcp-Session-Id header");
            return Mono.just(ResponseEntity.badRequest().build());
        }

        // 3) 校验会话是否存在
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            log.warn("Session not found: {}", sessionId);
            return Mono.just(ResponseEntity.notFound().build());
        }

        // 4) 解析并处理 JSON-RPC 消息。在 POST 通道
        try {
            Object parsed = JsonRpcCodec.parse(body);
            // 对于请求消息，结合客户端支持得响应方式，以及服务端对于请求内容的判断，综合决定采用 POST 直接返回响应，还是使用 SSE 通道返回响应
            if (parsed instanceof JsonRpcRequest request) {
                return handleRequest(state, request, accept);
            }
            // 对于通知消息，返回 202 Accepted
            else if (parsed instanceof JsonRpcNotification notification) {
                handleNotification(sessionId, notification);
                return Mono.just(ResponseEntity.accepted().build());
            }
        } catch (Exception e) {
            log.error("Failed to handle message from session {}", sessionId, e);
            String error = JsonRpcCodec.errorResponse(null, JsonRpcError.parseError(e.getMessage()));
            return Mono.just(ResponseEntity.ok().body(error)); // 直接以 JSON 返回解析错误
        }
        return Mono.just(ResponseEntity.ok().build());
    }

    /**
     * 处理请求
     * 其中，tool/call 方法需要动态确定返回方式
     */
    private Mono<ResponseEntity<String>> handleRequest(SessionState state, JsonRpcRequest request, String accept) {
        String sessionId = state.sessionId;
        log.info("Handling request, sessionId: {}, method: {}, id: {}",
                sessionId, request.getMethod(), request.getId());

        // 1) initialized 之前只允许 ping 或者 initialize 请求
        if (!McpConstants.METHOD_PING.equals(request.getMethod())
                && !McpConstants.METHOD_INITIALIZE.equals(request.getMethod())
                && !state.initialized) {
            log.warn("Request '{}' before initialized, sessionId: {}", request.getMethod(), sessionId);
            return Mono.just(ResponseEntity.ok()
                    .body(JsonRpcCodec.errorResponse(request.getId(), JsonRpcError.invalidRequest("Not initialized"))));
        }

        // 2) 正常请求处理
        return (switch (request.getMethod()) {
            case McpConstants.METHOD_PING ->
                Mono.just(ResponseEntity.ok().body(JsonRpcCodec.successResponse(request.getId(), Map.of())));

            case McpConstants.METHOD_INITIALIZE -> {
                log.info("Client initialize, sessionId: {}", sessionId);
                Map<String, Object> result = Map.of(
                        "protocolVersion", properties.getProtocolVersion(),
                        "capabilities", properties.getCapabilities(),
                        "serverInfo", properties.toServerInfo()
                );
                yield Mono.just(ResponseEntity.ok().body(JsonRpcCodec.successResponse(request.getId(), result)));
            }

            case McpConstants.METHOD_TOOLS_LIST ->
                Mono.fromCallable(toolRegistry::listToolDefinitions)
                        .subscribeOn(Schedulers.boundedElastic())
                        .map(tools -> Map.of("tools", tools))
                        .map(result -> JsonRpcCodec.successResponse(request.getId(), result))
                        .map(json -> ResponseEntity.ok().body(json));

            case McpConstants.METHOD_TOOLS_CALL -> {
                boolean clientSupportsSSE = accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
                yield executeTool(request.getParams())
                        .map(toolResult -> JsonRpcCodec.successResponse(request.getId(), toolResult))
                        .map(json -> {
                            // 对于工具调用请求。如果客户端支持 SSE 返回，就 SSE 返回，否则 POST 响应直接返回。
                            if (clientSupportsSSE) {
                                sendMessageEvent(state, json);
                                return ResponseEntity.accepted().<String>build();
                            }
                            return ResponseEntity.ok().body(json);
                        });
            }

            default ->
                Mono.just(ResponseEntity.ok().body(JsonRpcCodec.errorResponse(request.getId(), JsonRpcError.methodNotFound("Unknown method: " + request.getMethod()))));
        }).onErrorResume(e -> {
            log.error("Failed to handle request, sessionId: {}, method: {}", sessionId, request.getMethod(), e);
            return Mono.just(ResponseEntity.ok().body(JsonRpcCodec.errorResponse(request.getId(), JsonRpcError.internalError(e.getMessage()))));
        });
    }

    /**
     * 处理 initialize 通知
     */
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

    /**
     * 处理工具请求，将底层 invoke 包装成 Mono 实现响应式
     */
    @SuppressWarnings("unchecked")
    private Mono<CallToolResult> executeTool(Map<String, Object> params) {
        // 验证参数
        if (params == null) {
            return Mono.just(CallToolResult.error("Missing params"));
        }
        String name = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
        if (name == null) {
            return Mono.just(CallToolResult.error("Missing tool name"));
        }

        // 调用工具
        var tool = toolRegistry.getTool(name);
        return tool.map(mcpToolEntry ->
                // 把同步 invoke 包装成响应式 Mono
                // .fromCallable() 就是从同步方法
                // 当这个 Mono 被订阅的时候，就可以响应式地收到结果
                Mono.fromCallable(() -> mcpToolEntry.invoke(arguments))
                        // 指定专用线程池执行
                        .subscribeOn(Schedulers.boundedElastic())
                        // 处理正常结果
                        .map(result -> CallToolResult.success(result.toString()))
                        // 处理错误结果
                        .onErrorResume(e -> {
                            log.error("Tool execution failed: {}", name, e);
                            return Mono.just(CallToolResult.error("Tool execution failed: " + e.getMessage()));
                        })
        ).orElseGet(() -> Mono.just(CallToolResult.error("Tool not found: " + name)));
    }

    /**
     * 客户端 DELETE /mcp → 终止会话
     * <p>需携带 {@code Mcp-Session-Id} 请求头，缺失返回 400，无效返回 404。
     */
    @DeleteMapping(McpConstants.MCP_ENDPOINT)
    public Mono<ResponseEntity<Void>> deleteSession(
            @RequestHeader(value = McpConstants.HEADER_SESSION_ID, required = false) String sessionId,
            @RequestHeader(value = "Origin", required = false) String origin) {

        // 1) 校验 Origin
        if (isOriginForbidden(origin)) {
            return Mono.just(ResponseEntity.status(403).build());
        }

        // 2) 校验 Mcp-Session-Id 头是否缺失
        if (sessionId == null || sessionId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        // 3) 清理 session 资源
        if (!cleanupSession(sessionId, "deleted")) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return Mono.just(ResponseEntity.ok().build());
    }


    // ---------- 内部基础方法 ----------

    /**
     * 校验 Origin 头
     */
    private boolean isOriginForbidden(String origin) {
        List<String> allowed = properties.getAllowedOrigins();
        if (allowed.isEmpty()) {
            return false; // 未配置白名单，不校验
        }
        if (origin == null || origin.isBlank()) {
            return false; // 无 Origin 头（非浏览器请求），放行
        }
        boolean forbidden = !allowed.contains(origin);
        if (forbidden) log.warn("Origin '{}' not allowed", origin);
        return forbidden;
    }

    /**
     * 发送端点通知 SSE 事件
     */
    private void sendEndpointEvent(SessionState state) {
        ServerSentEvent<String> event = ServerSentEvent.<String>builder()
                .id(String.valueOf(state.eventId.incrementAndGet()))
                .event(McpConstants.SSE_EVENT_ENDPOINT)
                .data(McpConstants.MCP_ENDPOINT)
                .build();
        state.sink.tryEmitNext(event);
    }

    /**
     * 发送业务 SSE 事件
     */
    private void sendMessageEvent(SessionState state, String data) {
        ServerSentEvent<String> event = ServerSentEvent.<String>builder()
                .id(String.valueOf(state.eventId.incrementAndGet()))
                .event(McpConstants.SSE_EVENT_MESSAGE)
                .data(data)
                .build();
        Sinks.EmitResult result = state.sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.error("Failed to send SSE event, sessionId: {}, reason: {}", state.sessionId, result);
            state.sink.tryEmitComplete();
            sessions.remove(state.sessionId);
        }
    }

    /**
     * 清理会话资源
     * @return true 表示会话存在并被清理，false 表示会话不存在
     */
    private boolean cleanupSession(String sessionId, String reason) {
        SessionState state = sessions.remove(sessionId);
        if (state != null) {
            state.sink.tryEmitComplete();
            log.info("Session {} cleaned up ({})", sessionId, reason);
            return true;
        }
        return false;
    }

    /**
     * 使用 Sinks.many() 维持 sse 长连接的响应式事件推送
     */
    private static class SessionState {
        final String sessionId;
        /**
         * sink 是一个事件发送器
         * Sinks.many() 是一个能发出 0~N 个事件的槽
         * .mutilcast() 消息会被同时推送给所有订阅者（广播）
         * .onBackpressureBuffer() 如果订阅者处理速度跟不上，事件会先暂存在一个内部缓冲区，避免丢失
         * 调用 .tryEmitNext(event) 方法的时候，将 event 放入内部缓冲区，遍历当前所有订阅者，推送事件
         * ---------------------------------------------------------------------------------------
         * Reactor 一共有 3 种基础 Sink 类型，
         * Sinks.one 绑定 Mono 0 或 1 个元素 + 完成/错误 信号
         * Sinks.many 绑定 Flux 0~N 个元素 + 完成/错误 信号
         * Sinks.empty 绑定 Mono<Void> 完成/错误 信号
         * 通过 .asMono() 或者 .asFlux() 可以调出 sink 的绑定流，暴露给消费端后可以被订阅
         * 消费端通过 Mono/Flux 实例的 .subscribe(info -> {}) 可定义回调方法
         */
        final Sinks.Many<ServerSentEvent<String>> sink;
        final Flux<ServerSentEvent<String>> flux;
        final AtomicLong eventId = new AtomicLong(0);
        volatile boolean initialized = false;

        SessionState(String sessionId) {
            this.sessionId = sessionId;
            this.sink = Sinks.many().replay().latest();
            this.flux = sink.asFlux();
        }
    }
}
