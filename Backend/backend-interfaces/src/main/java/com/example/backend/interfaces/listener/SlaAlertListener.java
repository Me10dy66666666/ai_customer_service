package com.example.backend.interfaces.listener;

import com.example.backend.application.event.SlaAlertEvent;
import com.example.backend.interfaces.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlaAlertListener {
    private final ChatWebSocketHandler chatWebSocketHandler;

    @EventListener
    public void onSlaAlert(SlaAlertEvent event) {
        Map<String, Object> payload = Map.of(
                "type", "sla_alert",
                "workOrderId", event.getWorkOrderId(),
                "title", event.getTitle(),
                "level", event.getLevel().name().toLowerCase(),
                "message", event.getMessage()
        );
        chatWebSocketHandler.broadcastToAgents(payload);
    }
}
