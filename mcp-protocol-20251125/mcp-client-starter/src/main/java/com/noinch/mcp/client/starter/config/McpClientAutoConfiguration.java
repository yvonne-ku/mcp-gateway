package com.noinch.mcp.client.starter.config;

import com.noinch.mcp.client.starter.McpClientRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MCP Client 自动配置。
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(McpClientProperties.class)
public class McpClientAutoConfiguration {

    @Bean
    public McpClientRegistry mcpClientRegistry() {
        return new McpClientRegistry();
    }
}
