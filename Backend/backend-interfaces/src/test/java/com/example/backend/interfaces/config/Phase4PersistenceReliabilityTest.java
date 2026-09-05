package com.example.backend.interfaces.config;

import com.example.backend.domain.knowledge.model.KnowledgeOutbox;
import com.example.backend.domain.workorder.model.WorkOrder;
import com.example.backend.infrastructure.persistence.KnowledgeOutboxRepositoryImpl;
import com.example.backend.infrastructure.persistence.WorkOrderRepositoryImpl;
import com.example.backend.infrastructure.persistence.entity.KnowledgeOutboxEntity;
import com.example.backend.infrastructure.persistence.mapper.KnowledgeOutboxMapper;
import com.example.backend.infrastructure.persistence.mapper.WorkOrderMapper;
import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Phase4PersistenceReliabilityTest {

    @Test
    void outboxAssignsStableEventIdentityAndPropagatesGeneratedId() {
        KnowledgeOutboxMapper mapper = mock(KnowledgeOutboxMapper.class);
        doAnswer(invocation -> {
            KnowledgeOutboxEntity entity = invocation.getArgument(0);
            entity.setId(42L);
            return 1;
        }).when(mapper).insert(any(KnowledgeOutboxEntity.class));
        KnowledgeOutboxRepositoryImpl repository = new KnowledgeOutboxRepositoryImpl(mapper);
        KnowledgeOutbox first = outbox();
        KnowledgeOutbox sameBusinessEvent = outbox();

        repository.save(first);
        repository.save(sameBusinessEvent);

        assertThat(first.getId()).isEqualTo(42L);
        assertThat(first.getEventId()).isNotBlank().isEqualTo(sameBusinessEvent.getEventId());
    }

    @Test
    void workOrderUpdateRejectsStaleOptimisticLockVersion() {
        WorkOrderMapper mapper = mock(WorkOrderMapper.class);
        when(mapper.update(any())).thenReturn(0);
        WorkOrderRepositoryImpl repository = new WorkOrderRepositoryImpl(mapper);
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(7L);
        workOrder.setLockVersion(3L);

        assertThatThrownBy(() -> repository.save(workOrder))
                .isInstanceOf(ConcurrentModificationException.class)
                .hasMessageContaining("7");
    }

    @Test
    void outboxReplayOnlyTransitionsFailedEvents() {
        KnowledgeOutboxMapper mapper = mock(KnowledgeOutboxMapper.class);
        when(mapper.replayFailed(11L, LocalDateTime.MIN)).thenReturn(1);
        KnowledgeOutboxRepositoryImpl repository = new KnowledgeOutboxRepositoryImpl(mapper);

        assertThat(repository.replayFailed(11L, LocalDateTime.MIN)).isTrue();
    }

    private KnowledgeOutbox outbox() {
        KnowledgeOutbox outbox = new KnowledgeOutbox();
        outbox.setDocumentId(9L);
        outbox.setEventType(KnowledgeOutbox.EVENT_UPLOAD);
        outbox.setPayload("{\"version\":2}");
        outbox.setStatus(KnowledgeOutbox.STATUS_PENDING);
        outbox.setRetryCount(0);
        outbox.setMaxRetry(5);
        return outbox;
    }
}
