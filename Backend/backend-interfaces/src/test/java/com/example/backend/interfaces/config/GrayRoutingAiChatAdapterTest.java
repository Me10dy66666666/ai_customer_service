package com.example.backend.interfaces.config;

import com.example.backend.infrastructure.tsagent.GrayRoutingAiChatAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GrayRoutingAiChatAdapterTest {

    @Test
    void canaryRoutingIsStableAndHonorsKillSwitches() {
        String session = "session-stable-a";
        boolean first = GrayRoutingAiChatAdapter.useDsh(session, 20);
        boolean second = GrayRoutingAiChatAdapter.useDsh(session, 20);

        assertThat(second).isEqualTo(first);
        assertThat(GrayRoutingAiChatAdapter.useDsh(session, 0)).isFalse();
        assertThat(GrayRoutingAiChatAdapter.useDsh(session, 100)).isTrue();
    }
}
