package com.noinch.mcp.client.starter;

import com.noinch.mcp.protocol.core.jsonrpc.*;
import com.noinch.mcp.protocol.core.mcp.McpConstants;
import com.noinch.mcp.protocol.core.mcp.model.CallToolResult;
import com.noinch.mcp.protocol.core.mcp.model.ClientCapabilities;
import com.noinch.mcp.protocol.core.mcp.model.ClientInfo;
import com.noinch.mcp.protocol.core.mcp.model.ToolDefinition;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
@Builder
public class McpClient {

    // 连接相关
    private final WebClient webClient;  // reactive 提供
    private final String baseUrl;
    private final String clientName;
    private final String clientVersion;

    // 会话相关
    private String sessionId;
    @Getter
    private volatile boolean initialized = false;
    private Flux<ServerSentEvent<String>> sseFlux;  // SSE 连接的 Flux（用于保持连接活跃，异步拿到 sse 端口的推送信息），防止 Flux 没有被强引用被 GC 回收

    // 请求相关
    private final Map<Object, Sinks.One<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicLong requestIdGen = new AtomicLong(1);

    // 资源处理相关
    private Disposable sseDisposable;

    public McpClient(String baseUrl, String clientName, String clientVersion) {
        this.baseUrl = baseUrl;
        this.clientName = clientName;
        this.clientVersion = clientVersion;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }


    // ==================== 连接和生命周期 ====================


    /**
     * 建立与 MCP Server 的连接，完成 MCP 生命周期中的握手（initialize + initialized）
     * <ol>
     *   <li>GET /mcp 建立 SSE 连接，从响应头获取 sessionId</li>
     *   <li>发送 initialize 请求</li>
     *   <li>发送 initialized 通知</li>
     * </ol>
     */
    public Mono<Void> connect() {
        log.info("Connecting to MCP server at {}", this.baseUrl);
        // 请求 sse 连接端口，服务端返回 sessionId
        return establishSseConnection()
                // 发送 initialize 请求
                .flatMap(this::performInitialize)
                .doOnSuccess(v -> {
                    initialized = true;
                    log.info("MCP client connected and initialized");
                })
                .doOnError(e -> log.error("Failed to connect to MCP server: {}", e.getMessage()));
    }

    /**
     * 向 MCP Server 端 /mcp 端口发出连接建立请求
     * 服务端返回 sessionId
     */
    private Mono<String> establishSseConnection() {
        return webClient.get()
                .uri(McpConstants.MCP_ENDPOINT)
                .accept(MediaType.TEXT_EVENT_STREAM)
                // 手动处理 ClientResponse（reactive） 返回
                .exchangeToMono(response -> {
                    // 提取 sessionId
                    List<String> sessionIdHeaders = response.headers().header(McpConstants.HEADER_SESSION_ID);
                    if (sessionIdHeaders.isEmpty()) {
                        return Mono.error(new RuntimeException("Server did not return Mcp-Session-Id header"));
                    }
                    this.sessionId = sessionIdHeaders.get(0);
                    log.debug("SSE connection established, sessionId={}", this.sessionId);

                    // 对 SSE 连接端口发起 HTTP GET 请求后，服务器会源源不断推送 SSE 事件（每个事件是一个 ServerSentEvent 对象）
                    // 这里定义 SSE 事件流处理器
                    // 将 response 转成流式多次的 Flux，定义事件处理函数
                    // 这个 Flux 流需要一个消费者去订阅（订阅一个空消费者，防止连接关闭），才能保证被强引用，就不会被 GC
                    // 如果被 GC 会导致 SSE 连接断开
                    this.sseFlux = response.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                            .doOnNext(this::handleSseEvent)
                            .doOnError(e -> log.error("SSE stream error: {}", e.getMessage()))
                            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                            .share();
                    // TODO 这个地方不需要对业务事件进行任何的消费吗，万一是我 POST 请求的消息呢
                    this.sseDisposable = this.sseFlux.subscribe(
                            event -> {},
                            err -> log.error("SSE subscription error", err),
                            () -> log.debug("SSE stream completed")
                    );
                    return Mono.just(this.sessionId);
                });
    }

    /**
     * 过滤出事件类型为 “message” 的业务推送事件，并进行基本的处理
     */
    private void handleSseEvent(ServerSentEvent<String> event) {
        // 检查是否是业务推送事件
        if (!McpConstants.SSE_EVENT_MESSAGE.equals(event.event())) {
            return;
        }
        // 处理业务推送事件
        String data = event.data();
        if (data == null || data.isBlank()) {
            return;
        }
        try {
            JsonRpcResponse response = JsonRpcCodec.fromJson(data, JsonRpcResponse.class);
            if (response != null && response.getId() != null) {
                Object id = response.getId();
                Sinks.One<JsonRpcResponse> sink = pendingRequests.remove(id);
                // 判空。（为了防止异步交错，在POST响应和SSE端口重复处理同一个请求id，请让服务端永远只在其中之一的端口返回某一请求id的响应）
                if (sink != null) {
                    if (response.getError() != null) {
                        sink.tryEmitError(new RuntimeException("JSON-RPC error: " + response.getError()));
                    } else {
                        sink.tryEmitValue(response);
                    }
                }
            } else {
                log.debug("Received non-response message: {}", data);
            }
        } catch (Exception e) {
            log.error("Failed to parse SSE message: {}", data, e);
        }
    }

    /**
     * 执行 MCP 握手：发送 initialize 请求，等待响应，然后发送 initialized 通知
     */
    private Mono<Void> performInitialize(String sessionId) {
        Map<String, Object> params = Map.of(
                "protocolVersion", McpConstants.PROTOCOL_VERSION,
                "capabilities", new ClientCapabilities(),
                "clientInfo", ClientInfo.builder()
                        .name(clientName)
                        .version(clientVersion)
                        .build()
        );
        return sendRequest(McpConstants.METHOD_INITIALIZE, params)
                .flatMap(response -> {
                    log.debug("Initialize response received: {}", response);
                    // 发送 initialized 通知（单向，不需要等待响应）
                    return sendNotification(McpConstants.METHOD_INITIALIZED, Map.of());
                });
    }

    /**
     * 关闭客户端，终止会话并释放资源。
     * @return Mono<Void>
     */
    public Mono<Void> close() {
        // 1. sseFlux，SSE 连接订阅
        if (this.sseDisposable != null && !this.sseDisposable.isDisposed()) {
            this.sseDisposable.dispose();
        }
        // 2. pendingRequest，未完成的请求
        pendingRequests.values().forEach(sink -> sink.tryEmitError(new RuntimeException("Client closed")));
        pendingRequests.clear();
        // 3. 通知服务器删除会话
        if (sessionId == null) {
            return Mono.empty();
        }
        return webClient.delete()
                .uri(McpConstants.MCP_ENDPOINT)
                .header(McpConstants.HEADER_SESSION_ID, sessionId)
                .retrieve()
                .toBodilessEntity()
                .onErrorResume(e -> {
                    log.warn("Error closing session: {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }


    // ==================== 底层通信 ====================


    /**
     * 发送 JSON-RPC 请求，并返回等待响应的 Mono。
     */
    private Mono<JsonRpcResponse> sendRequest(String method, Map<String, Object> params) {
        // 创建请求
        long id = requestIdGen.getAndIncrement();
        JsonRpcRequest request = new JsonRpcRequest(id, method, params);

        // 处理 pendingRequests
        Sinks.One<JsonRpcResponse> sink = Sinks.one();
        pendingRequests.put(id, sink);

        // 发送请求
        return postJsonRpcMessage(request)
                // 模式 D：如果 POST 响应里有数据直接用，没有数据才去等待全局 SSE 的监听器发射数据
                .switchIfEmpty(sink.asMono())
                .timeout(Duration.ofSeconds(10))
                .doFinally(signal -> pendingRequests.remove(id));
    }

    /**
     * 发送 JSON-RPC 通知（不需要响应）。
     */
    private Mono<Void> sendNotification(String method, Map<String, Object> params) {
        // 创建请求
        JsonRpcNotification notification = new JsonRpcNotification(McpConstants.JSON_RPC_VERSION, method, params);

        // 发送请求
        return postJsonRpcMessage(notification).then();
    }

    /**
     * 发送 JSON-RPC 消息（请求或通知）。
     * @param message: JsonRpcRequest / JsonRpcNotification
     * @return Mono<JsonRpcResponse> 如果服务端在 POST 中直接返回了响应则携带数据，否则返回 Mono.empty()
     */
    private Mono<JsonRpcResponse> postJsonRpcMessage(Object message) {
        String json = JsonRpcCodec.toJson(message);
        return webClient.post()
                .uri(McpConstants.MCP_ENDPOINT)
                .header(McpConstants.HEADER_PROTOCOL_VERSION, McpConstants.PROTOCOL_VERSION)
                .header(McpConstants.HEADER_SESSION_ID, sessionId)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchangeToMono(resp -> {
                    // 1. 先判断是否是错误状态码，是则返回 Mono.error
                    if (resp.statusCode().isError()) {
                        return resp.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException("HTTP error " + resp.statusCode() + ": " + body)));
                    }

                    // 2. 获取 Content-Type 动态处理
                    MediaType contentType = resp.headers().contentType().orElse(MediaType.APPLICATION_JSON);
                    if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                        // 模式 A：服务端直接在 POST 响应体中返回了结果（绝大多数标准请求的默认行为）
                        return resp.bodyToMono(JsonRpcResponse.class);
                    } else if (MediaType.TEXT_EVENT_STREAM.isCompatibleWith(contentType)) {
                        // 模式 B：服务端针对该 POST 请求启用了局部流式响应
                        return resp.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                                .flatMap(event -> {
                                    if (McpConstants.SSE_EVENT_MESSAGE.equals(event.event()) && event.data() != null) {
                                        JsonRpcResponse r = JsonRpcCodec.fromJson(event.data(), JsonRpcResponse.class);
                                        return r != null ? Mono.just(r) : Mono.empty();
                                    }
                                    return Mono.empty();
                                })
                                .next(); // 提取局部流中的第一个有效响应
                    }
                    // 模式 C：202 Accepted 或无内容（如客户端发送的是 Notification），安全释放并返回空
                    return resp.releaseBody().then(Mono.empty());
                    // 模式 D：在 request 情况下返回空内容，需要去全局 sse 端口使用 sink 实例等待响应
                });
    }


    // ==================== 业务 API ====================


    /**
     * 列出服务端所有可用工具。
     * @return Flux<ToolDefinition> 工具定义流
     */
    @SuppressWarnings("unchecked")
    public Flux<ToolDefinition> listTools() {
        if (!initialized) {
            return Flux.error(new IllegalStateException("Client not connected or not initialized"));
        }
        return sendRequest(McpConstants.METHOD_TOOLS_LIST, Map.of())
                .flatMapMany(response -> {
                    Map<String, Object> result = (Map<String, Object>) response.getResult();
                    List<Map<String, Object>> toolsRaw = (List<Map<String, Object>>) result.get("tools");
                    List<ToolDefinition> tools = toolsRaw.stream()
                            .map(t -> ToolDefinition.builder()
                                    .name((String) t.get("name"))
                                    .description((String) t.get("description"))
                                    .inputSchema((Map<String, Object>) t.get("inputSchema"))
                                    .build())
                            .toList();
                    return Flux.fromIterable(tools);
                });
    }

    /**
     * 调用指定工具。
     * @param name 工具名称
     * @param arguments 参数
     * @return Mono<CallToolResult> 调用结果
     */
    @SuppressWarnings("unchecked")
    public Mono<CallToolResult> callTool(String name, Map<String, Object> arguments) {
        if (!initialized) {
            return Mono.error(new IllegalStateException("Client not connected or not initialized"));
        }
        Map<String, Object> params = Map.of(
                "name", name,
                "arguments", arguments != null ? arguments : Map.of()
        );
        return sendRequest(McpConstants.METHOD_TOOLS_CALL, params)
                .map(response -> {
                    Map<String, Object> result = (Map<String, Object>) response.getResult();
                    return CallToolResult.builder()
                            .content((List<Map<String, Object>>) result.get("content"))
                            .isError((Boolean) result.get("isError"))
                            .build();
                });
    }
}
