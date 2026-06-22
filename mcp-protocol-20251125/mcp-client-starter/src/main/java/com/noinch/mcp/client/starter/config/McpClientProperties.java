package com.noinch.mcp.client.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = McpClientProperties.PREFIX)
public class McpClientProperties {

    public static final String PREFIX = "noinch.mcp.client";

    private String clientName = "mcp-gateway-proxy";

    private String clientVersion = "1.0.0";
}
