package com.example.backend.interfaces.config;

import com.example.backend.interfaces.websocket.ChatWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final ChatWebSocketHandler handler;
    private final WebSocketAuthenticationInterceptor authenticationInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(ChatWebSocketHandler handler,
                           WebSocketAuthenticationInterceptor authenticationInterceptor,
                           @Value("${security.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
        this.handler = handler;
        this.authenticationInterceptor = authenticationInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/chat")
                .addInterceptors(authenticationInterceptor)
                .setAllowedOrigins(allowedOrigins);
    }
}
