package com.noinch.mcp.gateway.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "noinch.mcp.proxy")
public class ProxyConfig {

    // mcp-proxy基础URL
    private String baseUrl = "http://localhost:8080";

    /**
     * 代理超时时间
     */
    private Duration timeout = Duration.ofSeconds(300);

    /**
     * 最大内存大小（用于处理请求体）
     */
    private long maxInMemorySize = 256 * 1024; // 256KB

    /**
     * 连接超时
     */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * 读超时
     */
    private Duration readTimeout = Duration.ofSeconds(30);

    /**
     * WebClient 请求失败最大重试次数
     */
    private int maxRetries = 2;

    /**
     * WebClient 重试退避起始时间
     */
    private Duration retryBackoff = Duration.ofMillis(100);

    /**
     * 是否启用统计
     */
    private boolean enableStatistics = true;

    /**
     * 是否启用请求日志
     */
    private boolean enableRequestLogging = true;

    /**
     * mcp-client 服务地址（内部 RPC 调用）
     */
    private String mcpClientUrl = "http://localhost:8085";
}