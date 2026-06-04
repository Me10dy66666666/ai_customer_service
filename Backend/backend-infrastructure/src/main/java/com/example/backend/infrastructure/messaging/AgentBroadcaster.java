package com.example.backend.infrastructure.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Slf4j
@Component
public class AgentBroadcaster {

    private final List<Consumer<Map<String, Object>>> subscribers = new CopyOnWriteArrayList<>();

    private final ExecutorService broadcastPool = Executors.newFixedThreadPool(
            4,
            r -> { Thread t = new Thread(r, "bc-pool"); t.setDaemon(true); return t; });

    public void subscribe(Consumer<Map<String, Object>> listener) {
        subscribers.add(listener);
        log.debug("Broadcast subscriber added, total: {}", subscribers.size());
    }

    public void unsubscribe(Consumer<Map<String, Object>> listener) {
        subscribers.remove(listener);
        log.debug("Broadcast subscriber removed, total: {}", subscribers.size());
    }

    public void broadcast(Map<String, Object> payload) {
        for (Consumer<Map<String, Object>> s : subscribers) {
            broadcastPool.submit(() -> {
                try {
                    s.accept(payload);
                } catch (Exception e) {
                    log.warn("Broadcast subscriber error: {}", e.getMessage());
                }
            });
        }
    }
}
