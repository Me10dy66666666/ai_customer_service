package com.example.backend.interfaces.config;

import com.example.backend.domain.chat.service.AiChatPort;
import com.example.backend.infrastructure.dify.DifyAdapter;
import com.example.backend.infrastructure.dify.DifyClient;
import com.example.backend.infrastructure.dsh.DshAiChatAdapter;
import com.example.backend.infrastructure.dsh.DshGatewayClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Contract seam: DSH and Dify expose the same domain port to application services. */
class ProviderContractTest {

    @Test
    void dshAndDifyAdaptersPreserveTheSameBlockingPortShape() {
        DshGatewayClient dshGateway = mock(DshGatewayClient.class);
        DifyClient difyClient = mock(DifyClient.class);
        Map<String, String> response = Map.of("answer", "ok", "conversation_id", "c-1");
        when(dshGateway.sendMessage("q", "u", "c-1", Map.of())).thenReturn(response);
        when(difyClient.sendMessage("q", "u", "c-1", Map.of())).thenReturn(response);

        AiChatPort dsh = new DshAiChatAdapter(dshGateway);
        AiChatPort dify = new DifyAdapter(difyClient);

        assertThat(dsh.sendBlockingMessage("q", "u", "c-1", Map.of())).isEqualTo(response);
        assertThat(dify.sendBlockingMessage("q", "u", "c-1", Map.of())).isEqualTo(response);
    }
}
