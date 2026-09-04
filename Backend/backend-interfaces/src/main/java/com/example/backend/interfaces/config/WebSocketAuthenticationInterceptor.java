package com.example.backend.interfaces.config;

import com.example.backend.domain.auth.model.User;
import com.example.backend.domain.auth.repository.UserRepository;
import com.example.backend.infrastructure.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Set;

/**
 * Resolves WebSocket identity once during the handshake. Anonymous customer chat is still
 * supported, but privileged agent actions require the authenticated attributes populated here.
 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthenticationInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "authenticatedUserId";
    public static final String ATTR_ROLES = "authenticatedRoles";
    public static final String ATTR_USERNAME = "authenticatedUsername";
    public static final String ATTR_CHAT_SESSION_ID = "authenticatedChatSessionId";

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = resolveToken(request);
        MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams();
        String chatSessionId = query.getFirst("session_id");
        String chatToken = query.getFirst("chat_token");
        if (chatSessionId != null && chatToken != null
                && jwtUtils.validateChatSessionToken(chatToken, chatSessionId)) {
            attributes.put(ATTR_CHAT_SESSION_ID, chatSessionId);
        }
        if (token == null || !jwtUtils.validateToken(token)) {
            attributes.put(ATTR_ROLES, Set.of());
            return true;
        }

        String username = jwtUtils.getUsernameFromToken(token);
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.isDisabled()) {
            attributes.put(ATTR_ROLES, Set.of());
            return true;
        }

        attributes.put(ATTR_USERNAME, username);
        attributes.put(ATTR_USER_ID, user.getId());
        attributes.put(ATTR_ROLES, user.roleNames());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No resources are allocated during authentication.
    }

    private String resolveToken(ServerHttpRequest request) {
        MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams();
        String queryToken = query.getFirst("access_token");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken;
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String authorization = servletRequest.getServletRequest().getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                return authorization.substring(7);
            }
        }
        return null;
    }
}
