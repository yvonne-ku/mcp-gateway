package com.noinch.mcp.gateway.proxy.service.impl;

import com.noinch.mcp.client.starter.McpClient;
import com.noinch.mcp.client.starter.McpClientRegistry;
import com.noinch.mcp.gateway.auth.service.AuthApiKeyService;
import com.noinch.mcp.gateway.auth.tool.AuthReqTool;
import com.noinch.mcp.gateway.core.entity.AuthKeyEntity;
import com.noinch.mcp.gateway.core.entity.CallLogEntity;
import com.noinch.mcp.gateway.persist.mapper.CallLogMapper;
import com.noinch.mcp.gateway.proxy.service.ToolService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class ToolServiceImpl implements ToolService {

    private final AuthApiKeyService authApiKeyService;
    private final CallLogMapper callLogMapper;
    private final McpClientRegistry mcpClients;

    @Override
    public Mono<ResponseEntity<Object>> doCall(ServerWebExchange exchange,
                                               String serviceId,
                                               String toolName,
                                               Map<String, Object> arguments) {
        long startTime = System.currentTimeMillis();

        // 1. 检验 Api Key
        AuthKeyEntity authKeyEntity;
        String authKey = AuthReqTool.extractAuthKey(exchange.getRequest());
        try {
            authKeyEntity = authApiKeyService.validateAuthKey(authKey);
        } catch (Exception e) {
            log.warn("Auth validation failed: {}", e.getMessage());
            return Mono.just(ResponseEntity.status(401).body(Map.of("error", e.getMessage())));
        }

        // 2. 获取 McpClient
        McpClient client = mcpClients.getClient(serviceId);
        if (client == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        // 3. 调用工具
        String userId = authKeyEntity.getUserId();
        Long authKeyId = authKeyEntity.getId();
        return client.callTool(toolName, arguments)
                .flatMap(result -> {
                    boolean isError = result.getIsError() != null && result.getIsError();
                    // 4. 记录日志
                    recordCallLog(userId, authKeyId, serviceId, exchange, startTime, isError ? 400 : 200, isError ? "Tool execution error" : null);
                    return Mono.just(ResponseEntity.ok().body((Object) result));
                })
                .onErrorResume(e -> {
                    log.error("Tool call failed: service={}, tool={}", serviceId, toolName, e);
                    recordCallLog(userId, authKeyId, serviceId, exchange, startTime, 500, e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(Map.of("error", e.getMessage())));
                });
    }

    @Override
    public Mono<ResponseEntity<Object>> listToolsOfService(ServerWebExchange exchange,
                                                           String serviceId) {
        // 1. 检验 Api Key
        AuthKeyEntity authKeyEntity;
        String authKey = AuthReqTool.extractAuthKey(exchange.getRequest());
        try {
            authKeyEntity = authApiKeyService.validateAuthKey(authKey);
        } catch (Exception e) {
            return Mono.just(ResponseEntity.status(401).body(Map.of("error", e.getMessage())));
        }

        // 2. 获取 McpClient
        McpClient client = mcpClients.getClient(serviceId);
        if (client == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        // 3. 列出工具
        return client.listTools()
                .collectList()
                .map(tools -> ResponseEntity.ok().body((Object) Map.of("tools", tools)))
                .onErrorResume(e -> {
                    log.error("List tools failed: service={}", serviceId, e);
                    return Mono.just(ResponseEntity.internalServerError().body(Map.of("error", e.getMessage())));
                });
    }

    /**
     * 记录工具调用日志
     */
    private void recordCallLog(String userId, Long authKeyId, String serviceId, ServerWebExchange exchange,
                               long startTime, int statusCode, String errorMessage) {
        try {
            CallLogEntity logEntity = CallLogEntity.builder()
                    .userId(userId)
                    .authKeyId(authKeyId)
                    .serviceId(serviceId)
                    .requestPath(exchange.getRequest().getPath().value())
                    .requestMethod(exchange.getRequest().getMethod().name())
                    .clientIp(AuthReqTool.getClientIp(exchange.getRequest()))
                    .userAgent(exchange.getRequest().getHeaders().getFirst("User-Agent"))
                    .statusCode(statusCode)
                    .responseTimeMs((int) (System.currentTimeMillis() - startTime))
                    .errorMessage(errorMessage)
                    .createdAt(LocalDateTime.now())
                    .build();
            callLogMapper.insert(logEntity);
        } catch (Exception e) {
            log.warn("Failed to record call log", e);
        }
    }
}
