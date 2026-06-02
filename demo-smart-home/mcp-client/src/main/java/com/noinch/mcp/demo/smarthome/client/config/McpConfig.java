package com.noinch.mcp.demo.smarthome.client.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    private final AsyncMcpToolCallbackProvider asyncMcpToolCallbackProvider;

    public McpConfig(AsyncMcpToolCallbackProvider asyncMcpToolCallbackProvider) {
        this.asyncMcpToolCallbackProvider = asyncMcpToolCallbackProvider;
    }

    @Bean
    public ChatClient init(OpenAiChatModel openAiChatModel) {
        ToolCallback[] toolCallbacks = asyncMcpToolCallbackProvider.getToolCallbacks();
        return ChatClient.builder(openAiChatModel)
                .defaultToolCallbacks(toolCallbacks)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
