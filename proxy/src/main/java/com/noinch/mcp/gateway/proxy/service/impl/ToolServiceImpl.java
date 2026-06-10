package com.noinch.mcp.gateway.proxy.service.impl;

import com.noinch.mcp.client.starter.McpClient;
import com.noinch.mcp.gateway.auth.service.AuthApiKeyService;
import com.noinch.mcp.gateway.auth.tool.AuthReqTool;
import com.noinch.mcp.gateway.persist.mapper.CallLogMapper;
import com.noinch.mcp.gateway.proxy.service.ToolService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class ToolServiceImpl implements ToolService {

    private final AuthApiKeyService authApiKeyService;
    private final CallLogMapper callLogMapper;
    private final Map<String, McpClient> mcpClients;

    @Override
    public Mono<ResponseEntity<Object>> doCall(ServerWebExchange exchange,
                                               String serviceId,
                                               String toolName,
                                               Map<String, Object> arguments) {
        // 1. 检验 Api Key
        String authKey = AuthReqTool.extractAuthKey(exchange.getRequest());
        authApiKeyService.validateAuthKey(authKey);

        // 2. McpClient 调用 /call

        // 3. 记录调用

        return null;
    }

    @Override
    public Mono<ResponseEntity<Object>> listToolsOfService(ServerWebExchange exchange,
                                                           String serviceId) {
        // 1. 检验 Api Key
        String authKey = AuthReqTool.extractAuthKey(exchange.getRequest());
        authApiKeyService.validateAuthKey(authKey);

        // 2. McpClient 调用 /list

        return null;
    }
}
