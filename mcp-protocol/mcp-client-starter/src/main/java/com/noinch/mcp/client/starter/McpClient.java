package com.noinch.mcp.client.starter;

import com.noinch.mcp.protocol.core.jsonrpc.*;
import com.noinch.mcp.protocol.core.mcp.model.CallToolResult;
import com.noinch.mcp.protocol.core.mcp.model.ToolDefinition;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP 客户端
 *
 * 通过 SSE + JSON-RPC 与远端 MCP Server 通信，支持 MCP 2025-11-25 协议。
 * 自动处理协议握手（initialize → initialized 通知）。
 */
@Slf4j
public class McpClient {

    private final WebClient webClient;
    private final String serverUrl;
    private final String serverName;

    private String sessionId;
    private String messageEndpoint;
    private volatile boolean initialized = false;
    private Disposable sseSubscription;

    private final Map<Object, CompletableFuture<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicInteger requestId = new AtomicInteger(1);

    public McpClient(String serverUrl, String serverName) {
        this.serverUrl = serverUrl;
        this.serverName = serverName;
        this.webClient = WebClient.builder()
                .baseUrl(serverUrl)
                .build();
    }

    /**
     * 连接到 MCP Server
     * 1. 打开 SSE 连接，获取 sessionId 和消息端点地址
     * 2. 发送 initialize 请求
     * 3. 发送 initialized 通知
     */
    public void connect() {
        log.info("Connecting to MCP server: {} ({})", serverName, serverUrl);

        // Step 1: 打开 SSE 连接
        Flux<ServerSentEvent<String>> sseFlux = webClient.get()
                .uri("/sse")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<>() {});

        CountDownLatch latch = new CountDownLatch(1);

        sseSubscription = sseFlux
                .publishOn(Schedulers.boundedElastic())
                .subscribe(
                        event -> {
                            String eventName = event.event();
                            String data = event.data();

                            if ("endpoint".equals(eventName)) {
                                this.messageEndpoint = serverUrl + data;
                                if (data != null && data.contains("sessionId=")) {
                                    this.sessionId = data.substring(data.indexOf("sessionId=") + 10);
                                }
                                log.info("SSE connected: sessionId={}, messageEndpoint={}", sessionId, messageEndpoint);
                                latch.countDown();
                                sendInitialize();

                            } else if ("message".equals(eventName)) {
                                handleSseMessage(data);
                            }
                        },
                        error -> {
                            log.error("SSE connection error: {}", error.getMessage());
                            latch.countDown();
                        },
                        () -> log.info("SSE connection completed")
                );

        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timeout connecting to MCP server: " + serverUrl);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted connecting to MCP server", e);
        }

        // 等待初始化完成（最多 5 秒）
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

    private void sendInitialize() {
        log.info("Sending initialize request to {}", serverName);

        Map<String, Object> params = Map.of(
                "protocolVersion", "2025-11-25",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "mcp-client-starter", "version", "1.0.0")
        );

        int id = requestId.getAndIncrement();
        CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        JsonRpcRequest request = new JsonRpcRequest(id, "initialize", params);
        sendPostMessage(request);

        future.whenComplete((response, error) -> {
            if (error != null) {
                log.error("Initialize failed", error);
            } else {
                log.info("Initialize response from {}: {}", serverName, JsonRpcCodec.toJson(response));
                sendInitializedNotification();
                initialized = true;
            }
        });
    }

    private void sendInitializedNotification() {
        JsonRpcNotification notification = new JsonRpcNotification("2.0", "notifications/initialized", Map.of());
        sendPostMessage(notification);
        log.info("Initialized notification sent to {}", serverName);
    }

    private void handleSseMessage(String data) {
        try {
            JsonRpcResponse response = JsonRpcCodec.fromJson(data, JsonRpcResponse.class);
            if (response != null && response.getId() != null && response.getResult() != null) {
                CompletableFuture<JsonRpcResponse> future = pendingRequests.remove(response.getId());
                if (future != null) {
                    future.complete(response);
                    return;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            JsonRpcErrorResponse errorResponse = JsonRpcCodec.fromJson(data, JsonRpcErrorResponse.class);
            if (errorResponse != null && errorResponse.getId() != null) {
                CompletableFuture<JsonRpcResponse> future = pendingRequests.remove(errorResponse.getId());
                if (future != null) {
                    future.completeExceptionally(
                            new RuntimeException("JSON-RPC error: " + errorResponse.getError()));
                    return;
                }
            }
        } catch (Exception ignored) {
        }

        log.debug("Unmatched SSE message: {}", data);
    }

    private void sendPostMessage(Object message) {
        String json = JsonRpcCodec.toJson(message);
        webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/messages")
                        .queryParam("sessionId", sessionId)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .retrieve()
                .toBodilessEntity()
                .subscribe(
                        response -> log.debug("POST success: {}", json),
                        error -> log.error("POST failed: {}", error.getMessage())
                );
    }

    private CompletableFuture<JsonRpcResponse> sendRequest(String method, Map<String, Object> params) {
        int id = requestId.getAndIncrement();
        CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        JsonRpcRequest request = new JsonRpcRequest(id, method, params);
        sendPostMessage(request);

        return future;
    }

    @SuppressWarnings("unchecked")
    public List<ToolDefinition> listTools() {
        try {
            JsonRpcResponse response = sendRequest("tools/list", Map.of())
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

    public CallToolResult callTool(String name, Map<String, Object> arguments) {
        try {
            JsonRpcResponse response = sendRequest("tools/call", Map.of(
                    "name", name,
                    "arguments", arguments
            )).get(10, TimeUnit.SECONDS);

            @SuppressWarnings("unchecked")
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

    @PreDestroy
    public void close() {
        if (sseSubscription != null && !sseSubscription.isDisposed()) {
            sseSubscription.dispose();
        }
        log.info("MCP client {} disconnected", serverName);
    }

    public String getServerName() {
        return serverName;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
