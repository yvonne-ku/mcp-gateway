package com.noinch.mcp.demo.smarthome.server.light.config;

import com.noinch.mcp.demo.smarthome.server.light.service.LightService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolConfig {
    @Bean
    public ToolCallbackProvider toolCallbackProvider(LightService lightService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(lightService)
                .build();
    }
}
