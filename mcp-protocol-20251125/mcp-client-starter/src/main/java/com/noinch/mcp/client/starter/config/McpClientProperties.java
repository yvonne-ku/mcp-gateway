package com.noinch.mcp.client.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "noinch.mcp.client")
public class McpClientProperties {

    /** 本客户端的名称，用于 initialize 握手时告知服务端 */
    private String clientName = "mcp-client-starter";
    /** 本客户端的版本，用于 initialize 握手时告知服务端 */
    private String clientVersion = "1.0.0";

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
