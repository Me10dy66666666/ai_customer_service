package com.example.backend.infrastructure.persistence;

import com.example.backend.domain.chat.model.ConsultationLog;
import com.example.backend.domain.chat.repository.ConsultationLogRepository;
import com.example.backend.infrastructure.persistence.mapper.ConsultationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ConsultationLogRepositoryImpl implements ConsultationLogRepository {
    private final ConsultationLogMapper mapper;

    @Override
    public ConsultationLog save(ConsultationLog log) {
        com.example.backend.infrastructure.persistence.entity.ConsultationLog po = toEntity(log);
        if (log.getId() == null) mapper.insert(po); else mapper.update(po);
        return toDomain(mapper.selectById(po.getId()));
    }

    @Override public Optional<ConsultationLog> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }
    @Override public Optional<ConsultationLog> findLatestBySessionId(String sessionId) {
        return Optional.ofNullable(mapper.findFirstBySessionIdOrderByCreateTimeDesc(sessionId)).map(this::toDomain);
    }
    @Override public Optional<ConsultationLog> findLatestWithConversationIdBySessionId(String sessionId) {
        return Optional.ofNullable(mapper.findFirstBySessionIdAndDifyConversationIdIsNotNullOrderByCreateTimeDesc(sessionId)).map(this::toDomain);
    }
    @Override public List<ConsultationLog> findBySessionIdOrderByCreateTimeAsc(String sessionId) {
        return mapper.findBySessionIdOrderByCreateTimeAsc(sessionId).stream().map(this::toDomain).collect(Collectors.toList());
    }
    @Override public List<ConsultationLog> findByUserIdOrderByCreateTimeDesc(Long userId) {
        return mapper.findByUserIdOrderByCreateTimeDesc(userId).stream().map(this::toDomain).collect(Collectors.toList());
    }
    @Override public List<ConsultationLog> findByUserId(Long userId) {
        return mapper.findByUserId(userId).stream().map(this::toDomain).collect(Collectors.toList());
    }
    @Override public long countBetween(LocalDateTime start, LocalDateTime end) {
        return mapper.countByCreateTimeBetween(start, end);
    }
    @Override public int batchAssignUser(List<Long> ids, Long userId) {
        return mapper.batchAssignUser(ids, userId);
    }

    private ConsultationLog toDomain(com.example.backend.infrastructure.persistence.entity.ConsultationLog po) {
        ConsultationLog log = new ConsultationLog();
        log.setId(po.getId()); log.setSessionId(po.getSessionId()); log.setUserId(po.getUserId());
        log.setAgentId(po.getAgentId());
        log.setUserInput(po.getUserInput()); log.setAiResponse(po.getAiResponse());
        log.setDifyConversationId(po.getDifyConversationId()); log.setIntent(po.getIntent());
        log.setChannel(po.getChannel()); log.setDuration(po.getDuration()); log.setSatisfaction(po.getSatisfaction());
        log.setCreateTime(po.getCreateTime());
        return log;
    }

    private com.example.backend.infrastructure.persistence.entity.ConsultationLog toEntity(ConsultationLog log) {
        com.example.backend.infrastructure.persistence.entity.ConsultationLog po = new com.example.backend.infrastructure.persistence.entity.ConsultationLog();
        po.setId(log.getId()); po.setSessionId(log.getSessionId()); po.setUserId(log.getUserId());
        po.setAgentId(log.getAgentId());
        po.setUserInput(log.getUserInput()); po.setAiResponse(log.getAiResponse());
        po.setDifyConversationId(log.getDifyConversationId()); po.setIntent(log.getIntent());
        po.setChannel(log.getChannel()); po.setDuration(log.getDuration()); po.setSatisfaction(log.getSatisfaction());
        return po;
    }
}
