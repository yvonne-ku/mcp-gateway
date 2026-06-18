package com.noinch.mcp.gateway.proxy.controller;

import com.noinch.mcp.gateway.proxy.service.ToolService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/tools")
@AllArgsConstructor
public class HttpToolCallController {

    private final ToolService toolService;

    /**
     * 调用某个工具
     */
    @PostMapping("/{serviceId}/call")
    public Mono<ResponseEntity<Object>> callTool(ServerWebExchange exchange,
                                                 @PathVariable String serviceId,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        if (body == null || !body.containsKey("name")) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "Missing 'name' in request body")));
        }

        String toolName = (String) body.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) body.getOrDefault("arguments", Map.of());
        return toolService.doCall(exchange, serviceId, toolName, arguments);
    }

    /**
     * 列出某个服务下所有的工具
     */
    @GetMapping("/{serviceId}/list")
    public Mono<ResponseEntity<Object>> listToolsOfService (ServerWebExchange exchange,
                                                   @PathVariable String serviceId) {

        return toolService.listToolsOfService(exchange, serviceId);
    }

}
