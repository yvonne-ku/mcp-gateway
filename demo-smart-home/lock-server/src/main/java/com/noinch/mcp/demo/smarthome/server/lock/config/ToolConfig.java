package com.noinch.mcp.demo.smarthome.server.lock.config;

import com.noinch.mcp.demo.smarthome.server.lock.service.LockService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolConfig {

    @Bean
    public ToolCallbackProvider toolCallbackProvider(LockService lockService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(lockService)
                .build();
    }
}
