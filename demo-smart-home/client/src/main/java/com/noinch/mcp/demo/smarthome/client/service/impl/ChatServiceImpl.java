package com.noinch.mcp.demo.smarthome.client.service.impl;

import com.noinch.mcp.demo.smarthome.client.service.ChatService;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class ChatServiceImpl implements ChatService {

    /**
     * LLM 客户端
     */
    private final ChatClient chatClient;

    /**
     * 用于从远程获得已注册的 MCP Server
     * 这个实例是组件自己维护的，直接注入就可使用
     */
    private final AsyncMcpToolCallbackProvider asyncMcpToolCallbackProvider;
    
    @Override
    public Flux<String> mcpChat(String prompt) {
        return chatClient.prompt(prompt).stream().content();
    }

    @Override
    public Flux<String> mcpChatWithChosenService(String prompt, List<String> chosenServiceIds) {

        // 每次重新遍历远程工具，而不是在 init 的时候缓存在本地，确保可以使用到最新的工具回调
        List<ToolCallback> callbacks = new ArrayList<>();
        List.of(asyncMcpToolCallbackProvider.getToolCallbacks()).forEach(toolCallback -> {
            if (chosenServiceIds.contains(toolCallback.getToolDefinition().name())) {
                callbacks.add(toolCallback);
            }
        });

        // 调用 LLM 客户端，注入 prompt 和 toolCallbacks
        return chatClient.prompt(prompt).toolCallbacks(callbacks).stream().content();
    }

    @PostConstruct
    private void init(){
        log.info("ChatServiceImpl init");
        log.info("通过 AsyncMcpToolCallbackProvider 从远程获得已注册的 MCP Server 的工具回调");
        List.of(asyncMcpToolCallbackProvider.getToolCallbacks()).forEach(toolCallback -> {
            log.info(toolCallback.getToolMetadata().toString());
            log.info(toolCallback.getToolDefinition().name());
        });
        log.info("ChatServiceImpl init done");
    }

}
