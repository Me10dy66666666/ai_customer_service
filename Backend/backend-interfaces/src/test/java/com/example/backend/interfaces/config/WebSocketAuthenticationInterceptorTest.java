package com.example.backend.interfaces.config;

import com.example.backend.domain.auth.model.Role;
import com.example.backend.domain.auth.model.User;
import com.example.backend.domain.auth.repository.UserRepository;
import com.example.backend.infrastructure.security.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WebSocketAuthenticationInterceptorTest {

    private final JwtUtils jwtUtils = mock(JwtUtils.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final WebSocketAuthenticationInterceptor interceptor =
            new WebSocketAuthenticationInterceptor(jwtUtils, userRepository);

    @Test
    void anonymousHandshakeHasNoPrivilegedRoles() {
        Map<String, Object> attributes = handshake("/ws/chat", null);

        assertThat(attributes.get(WebSocketAuthenticationInterceptor.ATTR_ROLES))
                .isEqualTo(Set.of());
        assertThat(attributes).doesNotContainKey(WebSocketAuthenticationInterceptor.ATTR_USER_ID);
        verifyNoInteractions(jwtUtils, userRepository);
    }

    @Test
    void validAccessTokenUsesServerSideUserIdentityAndRoles() {
        User user = new User();
        user.setId(42L);
        user.setUsername("agent-a");
        user.setStatus(1);
        Role role = new Role();
        role.setRoleName("AGENT");
        user.setRoles(Set.of(role));
        when(jwtUtils.validateToken("signed-token")).thenReturn(true);
        when(jwtUtils.getUsernameFromToken("signed-token")).thenReturn("agent-a");
        when(userRepository.findByUsername("agent-a")).thenReturn(Optional.of(user));

        Map<String, Object> attributes = handshake(
                "/ws/chat?access_token=signed-token", null);

        assertThat(attributes.get(WebSocketAuthenticationInterceptor.ATTR_USER_ID)).isEqualTo(42L);
        assertThat(attributes.get(WebSocketAuthenticationInterceptor.ATTR_USERNAME)).isEqualTo("agent-a");
        assertThat(attributes.get(WebSocketAuthenticationInterceptor.ATTR_ROLES))
                .isEqualTo(Set.of("AGENT"));
    }

    @Test
    void invalidTokenCannotSupplyClientClaimedIdentity() {
        when(jwtUtils.validateToken("forged-token")).thenReturn(false);

        Map<String, Object> attributes = handshake(
                "/ws/chat?access_token=forged-token", "Bearer forged-token");

        assertThat(attributes.get(WebSocketAuthenticationInterceptor.ATTR_ROLES))
                .isEqualTo(Set.of());
        assertThat(attributes).doesNotContainKeys(
                WebSocketAuthenticationInterceptor.ATTR_USER_ID,
                WebSocketAuthenticationInterceptor.ATTR_USERNAME);
        verifyNoInteractions(userRepository);
    }

    @Test
    void validChatTokenBindsExactlyOneSessionToTheSocket() {
        when(jwtUtils.validateChatSessionToken("chat-token", "guest-session")).thenReturn(true);

        Map<String, Object> attributes = handshake(
                "/ws/chat?session_id=guest-session&chat_token=chat-token", null);

        assertThat(attributes.get(WebSocketAuthenticationInterceptor.ATTR_CHAT_SESSION_ID))
                .isEqualTo("guest-session");
        assertThat(attributes.get(WebSocketAuthenticationInterceptor.ATTR_ROLES))
                .isEqualTo(Set.of());
    }

    private Map<String, Object> handshake(String uri, String authorization) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", uri);
        servletRequest.setRequestURI(uri.split("\\?")[0]);
        if (uri.contains("?")) {
            servletRequest.setQueryString(uri.substring(uri.indexOf('?') + 1));
        }
        if (authorization != null) {
            servletRequest.addHeader("Authorization", authorization);
        }
        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                mock(org.springframework.http.server.ServerHttpResponse.class),
                mock(WebSocketHandler.class), attributes);
        assertThat(accepted).isTrue();
        return attributes;
    }
}
