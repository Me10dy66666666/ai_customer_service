package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.entity.ConsultationLog;
import com.example.backend.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*") // Allow frontend access
public class ChatController {

    @Autowired
    private ChatService chatService;
    
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @PostMapping("/send")
    public SseEmitter sendMessage(@RequestBody Map<String, Object> payload) {
        String sessionId = (String) payload.get("sessionId");
        String content = (String) payload.get("content");
        Integer userType = (Integer) payload.get("userType"); 
        Long userId = payload.get("userId") != null ? ((Number) payload.get("userId")).longValue() : null;

        SseEmitter emitter = new SseEmitter(180000L); // 3 minutes timeout

        if (sessionId == null || content == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("Session ID and content are required"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        executorService.execute(() -> {
            try {
                chatService.processStreamingMessage(sessionId, userId, userType, content, chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("Internal Server Error: " + e.getMessage()));
                } catch (IOException ex) {
                    // ignore
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @GetMapping("/history")
    public Result<List<ConsultationLog>> getHistory(@RequestParam String sessionId) {
        return Result.success(chatService.getHistory(sessionId));
    }
}
