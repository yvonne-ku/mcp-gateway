package com.noinch.mcp.protocol.client.controller;

import com.noinch.mcp.protocol.client.service.DeviceService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 设备控制 REST API
 * 通过自定义 MCP 协议与远程服务器通信
 */
@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /**
     * 列出所有 MCP 服务器上的可用工具
     */
    @GetMapping("/tools")
    public Flux<String> listTools() {
        return deviceService.listAllTools();
    }

    /**
     * 调用工具
     */
    @PostMapping("/tool/call")
    public Mono<String> callTool(@RequestBody Map<String, Object> request) {
        String toolName = (String) request.get("toolName");
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) request.getOrDefault("arguments", Map.of());
        String serverName = (String) request.getOrDefault("serverName", "");
        if (!serverName.isEmpty()) {
            return deviceService.callTool(serverName, toolName, args);
        }
        return deviceService.callToolSmart(toolName, args);
    }

    /**
     * 处理自然语言指令
     */
    @GetMapping("/chat")
    public Flux<String> chat(@RequestParam String prompt) {
        return deviceService.processPrompt(prompt);
    }
}
