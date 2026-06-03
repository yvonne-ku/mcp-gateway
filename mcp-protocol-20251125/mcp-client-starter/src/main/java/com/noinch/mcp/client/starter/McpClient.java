package com.noinch.mcp.client.starter;

import com.noinch.mcp.protocol.core.jsonrpc.*;
import com.noinch.mcp.protocol.core.mcp.McpConstants;
import com.noinch.mcp.protocol.core.mcp.model.CallToolResult;
import com.noinch.mcp.protocol.core.mcp.model.ClientCapabilities;
import com.noinch.mcp.protocol.core.mcp.model.ClientInfo;
import com.noinch.mcp.protocol.core.mcp.model.ToolDefinition;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP 客户端（2025-11-25 稳定版）
 *
 * <p>通过 SSE + JSON-RPC 与远端 MCP Server 通信。
 * 遵循 Streamable HTTP 传输规范：
 * <ul>
 *   <li>GET /mcp → 建立 SSE 连接，从响应头 {@code Mcp-Session-Id} 获取会话 ID</li>
 *   <li>POST /mcp → 发送 JSON-RPC 请求，通过 {@code Mcp-Session-Id} 头传递会话 ID</li>
 *   <li>所有请求携带 {@code MCP-Protocol-Version} 头</li>
 * </ul>
 */
@Slf4j
public class McpClient {

    private final WebClient webClient;
    private final String serverUrl;
    private final String serverName;
    private final String clientName;
    private final String clientVersion;

    private String sessionId;
    private volatile boolean initialized = false;
    // 连接引用，用于关闭连接
    private Disposable sseSubscription;

    private final Map<Object, CompletableFuture<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();
    // 自增 ID，用于唯一标识每个请求
    private final AtomicInteger requestId = new AtomicInteger(1);

    public McpClient(String serverUrl, String serverName, String clientName, String clientVersion) {
        this.serverUrl = serverUrl;
        this.serverName = serverName;
        this.clientName = clientName;
        this.clientVersion = clientVersion;
        this.webClient = WebClient.builder()
                .baseUrl(serverUrl)
                .build();
    }

    /**
     * 连接到 MCP Server
     * <ol>
     *   <li>GET /mcp 建立 SSE 连接，从响应头获取 sessionId</li>
     *   <li>发送 initialize 请求</li>
     *   <li>发送 initialized 通知</li>
     * </ol>
     */
    public void connect() {
        log.info("Connecting to MCP server: {} ({})", serverName, serverUrl);

        // 同步工具，让主线程等待 SSE 连接建立成功，再继续执行后面逻辑
        CountDownLatch latch = new CountDownLatch(1);

        // 1. 建立 SSE 连接，进行 MCP 生命周期的三次握手
        sseSubscription = webClient.get()
                .uri(McpConstants.MCP_ENDPOINT)
                .accept(MediaType.TEXT_EVENT_STREAM)
                // 1) 响应处理函数，仅处理一次，确定 SSE 连接成功
                .exchangeToFlux(response -> {
                    // 从响应头提取 Mcp-Session-Id，此时 sse 连接已经建立好
                    List<String> sessionIdHeaders = response.headers().header(McpConstants.HEADER_SESSION_ID);
                    if (!sessionIdHeaders.isEmpty()) {
                        this.sessionId = sessionIdHeaders.get(0);
                    }
                    log.info("SSE connected: sessionId={}", sessionId);

                    // 释放 latch：从而告知主线程 SSE 连接已建立
                    latch.countDown();

                    // 开始 MCP 三次握手
                    // 发送 initialize 请求
                    sendInitialize();
                    return response.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {});
                })
                // 在弹性线程池中处理 subscribe 操作，避免阻塞主线程
                .publishOn(Schedulers.boundedElastic())
                // 2) 监控 SSE 连接：每次收到服务端传来的 SSE 事件，都在这个操作里根据事件类型进行对应处理
                .subscribe(
                        event -> {
                            String eventName = event.event();
                            String data = event.data();
                            if (McpConstants.SSE_EVENT_MESSAGE.equals(eventName)) {
                                // 处理 SSE 事件的方法
                                handleSseMessage(data);
                            }
                        },
                        error -> {
                            log.error("SSE connection error: {}", error.getMessage());
                            latch.countDown();
                        },
                        () -> log.info("SSE connection completed")
                );

        // 2. 通过 CountDownLatch 检查 SSE 连接状态
        try {
            // 5 秒内归 0 → 返回 true → 不进 if → 正常继续
            // 5 秒没归 0 → 返回 false → 进 if → 手动抛异常，运行结束，方法出栈
            // 如果整个过程中有任何其他地方打断主线程，会进入 catch，将线程标记为被打断后，手动跑异常，运行结束，方法出栈
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timeout connecting to MCP server: " + serverUrl);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted connecting to MCP server", e);
        }

        // 3. 检查第三次握手，给等待时间 5 秒
        long deadline = System.currentTimeMillis() + 5000;
        while (!initialized && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (!initialized) {
            log.warn("MCP server {} initialization may not have completed", serverName);
        }
    }

    /**
     * 发送 initialize 请求
     */
    private void sendInitialize() {
        log.info("Sending initialize request to {}", serverName);

        // 构建 initialize 请求
        Map<String, Object> params = Map.of(
                "protocolVersion", McpConstants.PROTOCOL_VERSION,
                "capabilities", new ClientCapabilities(),
                "clientInfo", ClientInfo.builder().name(clientName).version(clientVersion).build()
        );
        int id = requestId.getAndIncrement();
        JsonRpcRequest request = new JsonRpcRequest(id, McpConstants.METHOD_INITIALIZE, params);

        // 构建 CompletableFuture，用于异步处理 initialize 请求的响应
        CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        // 发送 initialize 请求
        sendPostMessage(request);

        // 异步等待 initialize 的服务端响应，在 handleSSEMessage 方法中 future.complete(response)
        future.whenComplete((response, error) -> {
            if (error != null) {
                log.error("Initialize failed", error);
            } else {
                log.info("Initialize response from {}: {}", serverName, JsonRpcCodec.toJson(response));

                // 发送 initialized 通知，通知服务端客户端初始化完成
                sendInitializedNotification();

                initialized = true;
            }
        });
    }

    /**
     * 发送 initialized 通知，通知服务端客户端初始化完成
     */
    private void sendInitializedNotification() {
        JsonRpcNotification notification = new JsonRpcNotification(McpConstants.JSON_RPC_VERSION, McpConstants.METHOD_INITIALIZED, Map.of());
        sendPostMessage(notification);
        log.info("Initialized notification sent to {}", serverName);
    }

    private void handleSseMessage(String data) {
        try {
            JsonRpcResponse response = JsonRpcCodec.fromJson(data, JsonRpcResponse.class);
            if (response != null && response.getId() != null) {
                CompletableFuture<JsonRpcResponse> future = pendingRequests.remove(response.getId());
                if (future != null) {
                    if (response.getError() != null) {
                        future.completeExceptionally(
                                new RuntimeException("JSON-RPC error: " + response.getError()));
                    } else {
                        // 成功拿到响应，完成 CompletableFuture，返回响应结果给监听处
                        future.complete(response);
                    }
                    return;
                }
            }
        } catch (Exception ignored) {
        }

        log.debug("Unmatched SSE message: {}", data);
    }

    /**
     * 向服务端发送 POST 请求
     * @param message 请求 JsonRpcXXX
     */
    private void sendPostMessage(Object message) {
        String json = JsonRpcCodec.toJson(message);
        webClient.post()
                .uri(McpConstants.MCP_ENDPOINT)
                .header(McpConstants.HEADER_PROTOCOL_VERSION, McpConstants.PROTOCOL_VERSION)
                .header(McpConstants.HEADER_SESSION_ID, sessionId)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .retrieve()
                .toBodilessEntity()
                .subscribe(
                        response -> log.debug("POST success: {}", json),
                        error -> log.error("POST failed: {}", error.getMessage())
                );
    }

    /**
     * 向服务端发送 GET 请求
     * @param method 具体请求方法
     * @param params 请求方法所需参数
     * @return 请求结果
     */
    private CompletableFuture<JsonRpcResponse> sendRequest(String method, Map<String, Object> params) {
        int id = requestId.getAndIncrement();
        CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        JsonRpcRequest request = new JsonRpcRequest(id, method, params);
        sendPostMessage(request);

        return future;
    }

    /**
     * 客户端暴露的工具列表方法
     */
    @SuppressWarnings("unchecked")
    public List<ToolDefinition> listTools() {
        try {
            JsonRpcResponse response = sendRequest(McpConstants.METHOD_TOOLS_LIST, Map.of())
                    .get(5, TimeUnit.SECONDS);

            Map<String, Object> result = (Map<String, Object>) response.getResult();
            List<Map<String, Object>> toolsRaw = (List<Map<String, Object>>) result.get("tools");

            return toolsRaw.stream()
                    .map(t -> ToolDefinition.builder()
                            .name((String) t.get("name"))
                            .description((String) t.get("description"))
                            .inputSchema((Map<String, Object>) t.get("inputSchema"))
                            .build())
                    .toList();
        } catch (Exception e) {
            log.error("Failed to list tools from {}", serverName, e);
            return List.of();
        }
    }

    /**
     * 客户端暴露的工具回调方法
     */
    @SuppressWarnings("unchecked")
    public CallToolResult callTool(String name, Map<String, Object> arguments) {
        try {
            JsonRpcResponse response = sendRequest(McpConstants.METHOD_TOOLS_CALL, Map.of(
                    "name", name,
                    "arguments", arguments
            )).get(10, TimeUnit.SECONDS);

            Map<String, Object> result = (Map<String, Object>) response.getResult();
            return CallToolResult.builder()
                    .content((List<Map<String, Object>>) result.get("content"))
                    .isError((Boolean) result.get("isError"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to call tool {} on {}", name, serverName, e);
            return CallToolResult.error("Failed to call tool: " + e.getMessage());
        }
    }

    public void printAvailableTools() {
        List<ToolDefinition> tools = listTools();
        log.info("=== Tools from {} ===", serverName);
        for (ToolDefinition tool : tools) {
            log.info("  - {}: {}", tool.getName(), tool.getDescription());
        }
    }

    public String getServerName() {
        return serverName;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 当这个类（Bean）要被销毁、程序要关闭、服务要停止的时候，自动调用这个 close () 方法
     * 这个方法会关闭与服务端的会话，释放资源
     */
    public void close() {
        if (sessionId != null) {
            try {
                webClient.delete()
                        .uri(McpConstants.MCP_ENDPOINT)
                        .header(McpConstants.HEADER_SESSION_ID, sessionId)
                        .retrieve()
                        .toBodilessEntity()
                        .subscribe(
                                response -> log.debug("Session {} terminated", sessionId),
                                error -> log.warn("Failed to terminate session {}: {}", sessionId, error.getMessage())
                        );
            } catch (Exception e) {
                log.warn("Error deleting session {}", sessionId, e);
            }
        }
        if (sseSubscription != null && !sseSubscription.isDisposed()) {
            sseSubscription.dispose();
        }
        log.info("MCP client {} disconnected", serverName);
    }
}
