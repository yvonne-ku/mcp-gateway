package com.noinch.mcp.demo.smarthome.custom.client.controller;

import com.noinch.mcp.client.starter.McpClient;
import com.noinch.mcp.protocol.core.mcp.model.CallToolResult;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final Map<String, McpClient> mcpClients;

    public ChatController(Map<String, McpClient> mcpClients) {
        this.mcpClients = mcpClients;
    }

    @GetMapping("/tools")
    public Map<String, Object> listAllTools() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : mcpClients.entrySet()) {
            result.put(entry.getKey(), entry.getValue().listTools());
        }
        return result;
    }

    @PostMapping("/tool/call")
    public Object callTool(@RequestBody Map<String, Object> request) {
        String toolName = (String) request.get("toolName");
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) request.getOrDefault("arguments", Map.of());
        String serverName = (String) request.getOrDefault("serverName", "");

        McpClient client;
        if (!serverName.isEmpty()) {
            client = mcpClients.get(serverName);
            if (client == null) {
                return Map.of("error", "Server not found: " + serverName);
            }
        } else {
            client = mcpClients.values().stream()
                    .filter(c -> c.listTools().stream()
                            .anyMatch(t -> t.getName().equals(toolName)))
                    .findFirst()
                    .orElse(null);
            if (client == null) {
                return Map.of("error", "No server found with tool: " + toolName);
            }
        }

        CallToolResult result = client.callTool(toolName, args);
        return Map.of(
                "serverName", client.getServerName(),
                "content", result.getContent(),
                "isError", result.getIsError()
        );
    }

    @GetMapping("/chat")
    public List<String> chat(@RequestParam String prompt) {
        return mcpClients.values().stream()
                .flatMap(client -> client.listTools().stream())
                .filter(t -> t.getName().toLowerCase().contains(prompt.toLowerCase())
                        || (t.getDescription() != null
                            && t.getDescription().toLowerCase().contains(prompt.toLowerCase())))
                .map(t -> t.getName() + ": " + t.getDescription())
                .toList();
    }
}
