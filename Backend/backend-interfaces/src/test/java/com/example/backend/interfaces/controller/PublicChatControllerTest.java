package com.example.backend.interfaces.controller;

import com.example.backend.application.service.ChatApplicationService;
import com.example.backend.application.service.ChatMessageService;
import com.example.backend.common.Result;
import com.example.backend.common.exception.UnauthorizedException;
import com.example.backend.domain.chat.model.SessionState;
import com.example.backend.domain.chat.service.SessionStatePort;
import com.example.backend.infrastructure.security.JwtUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicChatControllerTest {

    private final SessionStatePort statePort = mock(SessionStatePort.class);
    private final JwtUtils jwtUtils = new JwtUtils(
            "test-only-secret-with-at-least-thirty-two-bytes", 60_000);
    private final PublicChatController controller = new PublicChatController(
            mock(ChatApplicationService.class), mock(ChatMessageService.class), statePort, jwtUtils);

    @Test
    void issuedSessionTokenOnlyAuthorizesItsOwnSession() {
        Result<Map<String, String>> issued = controller.createSession();
        String sessionId = issued.getData().get("sessionId");
        String token = issued.getData().get("sessionToken");
        when(statePort.getState(sessionId)).thenReturn(SessionState.AI);

        Result<Map<String, Object>> status = controller.getSessionStatus(sessionId, token);

        assertThat(status.getData().get("sessionId")).isEqualTo(sessionId);
        assertThatThrownBy(() -> controller.getSessionStatus("guest-other", token))
                .isInstanceOf(UnauthorizedException.class);
    }
}
