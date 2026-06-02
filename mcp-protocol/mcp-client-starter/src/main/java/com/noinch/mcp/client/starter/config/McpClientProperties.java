package com.noinch.mcp.client.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "mcp.client")
public class McpClientProperties {

    /** 要连接的 MCP Server 列表 */
    private List<ServerConfig> servers = new ArrayList<>();

    @Data
    public static class ServerConfig {
        /** MCP Server 名称，用于日志和路由 */
        private String name;
        /** MCP Server 地址，如 http://localhost:8083 */
        private String url;
    }
}
