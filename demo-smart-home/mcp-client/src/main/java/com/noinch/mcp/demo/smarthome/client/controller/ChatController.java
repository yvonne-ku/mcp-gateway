package com.noinch.mcp.demo.smarthome.client.controller;


import com.noinch.mcp.demo.smarthome.client.service.ChatService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/mcp")
@AllArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @RequestMapping("/chat")
    public Flux<String> chat(@RequestParam String prompt) {
        return chatService.mcpChat(prompt);
    }

    @RequestMapping("/chatWithChosenService")
    public Flux<String> chatWithChosenService(String prompt, List<String> chosenServiceIds) {
        return chatService.mcpChatWithChosenService(prompt, chosenServiceIds);
    }
}
