package com.noinch.mcp.server.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@Data
@ConfigurationProperties(prefix = "mcp.server")
public class McpServerProperties {

    /** MCP 服务器默认名称，Client 端用来做日志/路由区分 */
    private String name = "mcp-server";

    /** Server-Starter 自身版本，Client-Starter 用于检验版本匹配 */
    private String version = "1.0.0";

    /** MCP 协议版本，即 Server-Starter 遵循的 MCP 版本规范 */
    private String protocolVersion = "2025-11-25";

    /** Server 向 Client 声明自己支持的能力。默认支持工具调用。 */
    private Map<String, Object> capabilities = Map.of("tools", Map.of());

}
