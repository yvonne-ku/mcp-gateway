package com.noinch.mcp.protocol.client.service;

import com.noinch.mcp.protocol.client.protocol.McpClient;
import com.noinch.mcp.protocol.core.mcp.model.CallToolResult;
import com.noinch.mcp.protocol.core.mcp.model.ToolDefinition;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备服务
 * 通过 MCP 协议调用远程服务器上的设备工具
 */
@Slf4j
@Service
public class DeviceService {

    private final Map<String, McpClient> mcpClients = new ConcurrentHashMap<>();
    private final List<String> serverUrls;

    public DeviceService() {
        this.serverUrls = List.of(
                "http://localhost:8083",
                "http://localhost:8084"
        );
    }

    @PostConstruct
    public void init() {
        for (String url : serverUrls) {
            try {
                String name = url.contains("8083") ? "light-server" : "lock-server";
                McpClient client = new McpClient(url, name);
                client.connect();
                mcpClients.put(name, client);
                client.printAvailableTools();
                log.info("Connected to {} at {}", name, url);
            } catch (Exception e) {
                log.error("Failed to connect to MCP server at {}", url, e);
            }
        }
    }

    /**
     * 列出所有可用工具
     */
    public Flux<String> listAllTools() {
        return Flux.fromIterable(mcpClients.entrySet())
                .flatMap(entry -> {
                    String serverName = entry.getKey();
                    List<ToolDefinition> tools = entry.getValue().listTools();
                    List<String> lines = new ArrayList<>();
                    lines.add("=== " + serverName + " ===");
                    for (ToolDefinition tool : tools) {
                        lines.add("- " + tool.getName() + ": " + tool.getDescription());
                    }
                    return Flux.fromIterable(lines);
                });
    }

    /**
     * 调用指定服务器上的工具
     */
    public Mono<String> callTool(String serverName, String toolName, Map<String, Object> arguments) {
        McpClient client = mcpClients.get(serverName);
        if (client == null) {
            return Mono.just("Server not found: " + serverName);
        }
        return Mono.fromCallable(() -> {
            CallToolResult result = client.callTool(toolName, arguments);
            if (result.getIsError() != null && result.getIsError()) {
                return "Error: " + extractText(result);
            }
            return extractText(result);
        });
    }

    /**
     * 智能调用：根据工具名称自动在所有服务器中查找并调用
     */
    public Mono<String> callToolSmart(String toolName, Map<String, Object> arguments) {
        for (Map.Entry<String, McpClient> entry : mcpClients.entrySet()) {
            List<ToolDefinition> tools = entry.getValue().listTools();
            boolean found = tools.stream().anyMatch(t -> t.getName().equals(toolName));
            if (found) {
                return callTool(entry.getKey(), toolName, arguments);
            }
        }
        return Mono.just("Tool not found: " + toolName);
    }

    /**
     * 处理自然语言指令（简易匹配）
     */
    public Flux<String> processPrompt(String prompt) {
        log.info("Processing prompt: {}", prompt);

        return Flux.fromIterable(mcpClients.keySet())
                .flatMap(serverName -> {
                    McpClient client = mcpClients.get(serverName);
                    if (client == null) return Flux.empty();

                    List<ToolDefinition> tools = client.listTools();
                    List<Flux<String>> results = new ArrayList<>();

                    for (ToolDefinition tool : tools) {
                        if (matchesPrompt(prompt, tool)) {
                            log.info("Matched tool: {} on {}", tool.getName(), serverName);
                            results.add(callTool(serverName, tool.getName(), extractArgs(prompt, tool))
                                    .map(text -> "[" + serverName + "] " + text)
                                    .flux());
                        }
                    }

                    if (results.isEmpty()) {
                        return Flux.just("No matching tool found for: " + prompt);
                    }
                    return Flux.merge(results);
                });
    }

    private boolean matchesPrompt(String prompt, ToolDefinition tool) {
        String p = prompt.toLowerCase();
        String t = tool.getName().toLowerCase();
        String d = tool.getDescription() != null ? tool.getDescription().toLowerCase() : "";

        return p.contains(t) || p.contains(d) ||
                (t.contains("light") && (p.contains("灯") || p.contains("光"))) ||
                (t.contains("lock") && (p.contains("锁") || p.contains("门"))) ||
                (t.contains("device") && (p.contains("设备") || p.contains("list"))) ||
                (t.contains("brightness") && (p.contains("亮度") || p.contains("bright"))) ||
                (t.contains("color") && (p.contains("颜色") || p.contains("color"))) ||
                (t.contains("battery") && (p.contains("电池") || p.contains("battery"))) ||
                (t.contains("password") && (p.contains("密码") || p.contains("password")));
    }

    private Map<String, Object> extractArgs(String prompt, ToolDefinition tool) {
        return Map.of();
    }

    private String extractText(CallToolResult result) {
        if (result.getContent() != null && !result.getContent().isEmpty()) {
            Object text = result.getContent().get(0).get("text");
            return text != null ? text.toString() : "No text content";
        }
        return "Empty result";
    }
}
