package com.noinch.mcp.demo.smarthome.client.service;

import reactor.core.publisher.Flux;

import java.util.List;

public interface ChatService {

    /**
     * 使用 {@link McpConfig} 中配置的默认 Tools 列表与 LLM 对话
     * @param prompt 提示词
     * @return 回答
     */
    Flux<String> mcpChat(String prompt);

    /**
     * 指定工具覆盖 {@link McpConfig} 的默认 Tools 配置，与 LLM 对话
     * @param prompt 提示词
     * @param chosenServiceIds 指定工具 ID
     * @return 回答
     */
    Flux<String> mcpChatWithChosenService(String prompt, List<String> chosenServiceIds);
}

