package com.noinch.mcp.server.starter.config;

import com.noinch.mcp.server.starter.McpController;
import com.noinch.mcp.server.starter.McpToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MCP Server SSE 自动配置
 * 创建 {@link McpToolRegistry}，内部自动将注册的工具包装为对应 bean 存入上下文
 * 创建 {@link McpController} 处理 SSE 连接和 JSON-RPC 消息。
 */
@AutoConfiguration
@EnableConfigurationProperties(McpServerProperties.class)
public class McpServerAutoConfiguration {

    @Bean
    public McpToolRegistry mcpToolRegistry() {
        return new McpToolRegistry();
    }

    @Bean
    public McpController mcpController(McpToolRegistry toolRegistry, McpServerProperties properties) {
        return new McpController(toolRegistry, properties);
    }
}
