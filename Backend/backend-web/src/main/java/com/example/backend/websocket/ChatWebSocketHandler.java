package com.example.backend.websocket;

import com.example.backend.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private ChatService chatService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sendMessage(session, "connected", "WebSocket connected");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        CompletableFuture.runAsync(() -> processChatMessage(session, message.getPayload()));
    }

    private void processChatMessage(WebSocketSession session, String payload) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String sessionId = data.get("sessionId") != null ? String.valueOf(data.get("sessionId")) : null;
            String content = data.get("content") != null ? String.valueOf(data.get("content")) : null;

            if (sessionId == null || sessionId.isBlank() || content == null || content.isBlank()) {
                sendMessage(session, "error", "sessionId 和 content 不能为空");
                return;
            }

            Integer userType = parseInteger(data.get("userType"), 0);
            Long userId = parseLong(data.get("userId"));

            chatService.processStreamingMessage(sessionId, userId, userType, content, chunk -> {
                sendMessage(session, "chunk", chunk);
            });

            sendMessage(session, "done", "");
        } catch (Exception e) {
            sendMessage(session, "error", e.getMessage());
        }
    }

    private Integer parseInteger(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void sendMessage(WebSocketSession session, String type, String content) {
        try {
            if (!session.isOpen()) {
                return;
            }
            Map<String, Object> response = new HashMap<>();
            response.put("type", type);
            response.put("content", content);
            String json = objectMapper.writeValueAsString(response);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    }
}
