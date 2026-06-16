package com.noinch.mcp.client.starter.config;

import com.noinch.mcp.client.starter.McpClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Client 自动配置
 * 从 {@code noinch.mcp.client.servers} 配置读取 MCP Server 列表，
 * 自动创建并连接 {@link McpClient}，以 {@code Map<String, McpClient>} 形式暴露。
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(McpClientProperties.class)
public class McpClientAutoConfiguration {

    private final List<McpClient> managedClients = new ArrayList<>();

    /**
     * 通过配置文件创建 McpClient，并存入本地 managedClients 管理
     */
    @Bean
    public Map<String, McpClient> mcpClients(McpClientProperties properties) {
        Map<String, McpClient> clientMap = new LinkedHashMap<>();
        // 遍历配置文件中每一个 server 信息，创建对应的 McpClient
        for (McpClientProperties.ServerConfig config : properties.getServers()) {
            McpClient client = new McpClient(config.getUrl(), properties.getClientName(), properties.getClientVersion());
            client.connect().block(Duration.ofSeconds(30));
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
                client.close().block(Duration.ofSeconds(10));
            } catch (Exception e) {
                log.warn("Error closing MCP client", e);
            }
        }
    }
}
