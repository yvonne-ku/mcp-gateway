package com.noinch.mcp.gateway.proxy.service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface ToolService {

    Mono<ResponseEntity<Object>> doCall(ServerWebExchange exchange, String serviceId, String toolName, Map<String, Object> arguments);

    Mono<ResponseEntity<Object>> listToolsOfService(ServerWebExchange exchange, String serviceId);
}
