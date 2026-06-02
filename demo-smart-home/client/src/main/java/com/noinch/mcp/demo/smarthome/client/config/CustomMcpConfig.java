package com.noinch.mcp.demo.smarthome.client.config;

import io.modelcontextprotocol.client.McpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.customizer.McpAsyncClientCustomizer;
import org.springframework.stereotype.Component;

import java.time.Duration;


@Slf4j
@Component
public class CustomMcpConfig implements McpAsyncClientCustomizer {

    /**
     * 实现 McpAsyncClientCustomizer 接口的定制化方法 customize，
     * 这个方法有两个入参，是自动注入的入参，spec 用于进行定制化
     * @param name the name of the MCP client being customized
     * @param spec the async specification to customize
     */
    @Override
    public void customize(String name, McpClient.AsyncSpec spec) {
        log.info("进行定制化的 Mcp Client 的名称是：{}", name);
        spec.requestTimeout(Duration.ofSeconds(30));
    }
}
