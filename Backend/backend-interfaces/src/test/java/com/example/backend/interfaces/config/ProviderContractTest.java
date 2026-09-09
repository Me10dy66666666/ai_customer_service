package com.example.backend.interfaces.config;

import com.example.backend.domain.chat.service.AgentRuntimePort;
import com.example.backend.infrastructure.dify.DifyAdapter;
import com.example.backend.infrastructure.dify.DifyClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Contract seam: compatibility providers expose the same domain port. */
class ProviderContractTest {

    @Test
    void difyAdapterPreservesTheBlockingPortShape() {
        DifyClient difyClient = mock(DifyClient.class);
        Map<String, String> response = Map.of("answer", "ok", "conversation_id", "c-1");
        when(difyClient.sendMessage("q", "u", "c-1", Map.of())).thenReturn(response);

        AgentRuntimePort dify = new DifyAdapter(difyClient);

        assertThat(dify.sendBlockingMessage("q", "u", "c-1", Map.of())).isEqualTo(response);
    }
}
