package com.noinch.mcp.server.starter.config;

import com.noinch.mcp.protocol.core.mcp.model.ServerCapabilities;
import com.noinch.mcp.protocol.core.mcp.model.ServerInfo;
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

    /** Server 向 Client 声明自己支持的能力，默认仅支持工具调用。 */
    private ServerCapabilities capabilities = ServerCapabilities.builder().tools(Map.of()).build();

    /** 构建 ServerInfo，用于 initialize 响应 */
    public ServerInfo toServerInfo() {
        return ServerInfo.builder().name(name).version(version).build();
    }

}
