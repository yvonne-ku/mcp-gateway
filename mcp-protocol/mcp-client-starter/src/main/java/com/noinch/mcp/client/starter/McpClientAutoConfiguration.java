package com.noinch.mcp.client.starter;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Client 自动配置
 *
 * 从 {@code mcp.client.servers} 配置读取 MCP Server 列表，
 * 自动创建并连接 {@link McpClient}，以 {@code Map<String, McpClient>} 形式暴露。
 *
 * 使用方式：
 * <pre>{@code
 * @Autowired
 * private Map<String, McpClient> mcpClients;
 *
 * mcpClients.get("light").callTool("turn_on", args);
 * }</pre>
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(McpClientProperties.class)
public class McpClientAutoConfiguration {

    private final List<McpClient> managedClients = new ArrayList<>();

    @Bean
    public Map<String, McpClient> mcpClients(McpClientProperties properties) {
        Map<String, McpClient> clientMap = new LinkedHashMap<>();
        for (McpClientProperties.ServerConfig config : properties.getServers()) {
            McpClient client = new McpClient(config.getUrl(), config.getName());
            client.connect();
            clientMap.put(config.getName(), client);
            managedClients.add(client);
            log.info("Connected to MCP server: {} ({})", config.getName(), config.getUrl());
        }
        if (!clientMap.isEmpty()) {
            log.info("Total MCP clients connected: {}", clientMap.size());
        }
        return clientMap;
    }

    @PreDestroy
    public void destroy() {
        for (McpClient client : managedClients) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Error closing MCP client: {}", client.getServerName(), e);
            }
        }
    }
}
