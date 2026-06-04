package com.example.backend.interfaces.controller;

import com.example.backend.application.service.ChatMessageService;
import com.example.backend.common.Result;
import com.example.backend.domain.chat.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatMessageController {
    private final ChatMessageService chatMessageService;

    @GetMapping("/messages/{sessionId}")
    public Result<List<ChatMessage>> getHistory(@PathVariable String sessionId) {
        return Result.success(chatMessageService.getHistory(sessionId));
    }
}
