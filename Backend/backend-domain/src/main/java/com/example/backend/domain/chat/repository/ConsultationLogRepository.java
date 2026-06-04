package com.example.backend.domain.chat.repository;

import com.example.backend.domain.chat.model.ConsultationLog;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConsultationLogRepository {
    ConsultationLog save(ConsultationLog log);
    Optional<ConsultationLog> findById(Long id);
    Optional<ConsultationLog> findLatestBySessionId(String sessionId);
    Optional<ConsultationLog> findLatestWithConversationIdBySessionId(String sessionId);
    List<ConsultationLog> findBySessionIdOrderByCreateTimeAsc(String sessionId);
    List<ConsultationLog> findByUserIdOrderByCreateTimeDesc(Long userId);
    List<ConsultationLog> findByUserId(Long userId);
    long countBetween(LocalDateTime start, LocalDateTime end);
    int batchAssignUser(List<Long> ids, Long userId);
}
