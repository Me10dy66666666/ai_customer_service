package com.example.backend.infrastructure.persistence;

import com.example.backend.domain.chat.model.ChatMessage;
import com.example.backend.domain.chat.repository.ChatMessageRepository;
import com.example.backend.infrastructure.persistence.mapper.ChatMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepository {

    private final ChatMessageMapper mapper;

    @Override
    public ChatMessage save(ChatMessage message) {
        com.example.backend.infrastructure.persistence.entity.ChatMessage po = toEntity(message);
        mapper.insert(po);
        return toDomain(po);
    }

    @Override
    public List<ChatMessage> findBySessionIdOrderByMessageSeqAsc(String sessionId) {
        return mapper.findBySessionIdOrderByMessageSeqAsc(sessionId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Integer getMaxMessageSeqBySessionId(String sessionId) {
        Integer max = mapper.selectMaxMessageSeqBySessionId(sessionId);
        return max != null ? max : 0;
    }

    private ChatMessage toDomain(com.example.backend.infrastructure.persistence.entity.ChatMessage po) {
        ChatMessage msg = ChatMessage.create(po.getSessionId(), po.getSenderType(),
                po.getSenderId(), po.getContent(), po.getMessageSeq());
        msg.setId(po.getId());
        msg.setCreateTime(po.getCreateTime());
        return msg;
    }

    private com.example.backend.infrastructure.persistence.entity.ChatMessage toEntity(ChatMessage msg) {
        com.example.backend.infrastructure.persistence.entity.ChatMessage po =
                new com.example.backend.infrastructure.persistence.entity.ChatMessage();
        po.setSessionId(msg.getSessionId());
        po.setSenderType(msg.getSenderType());
        po.setSenderId(msg.getSenderId());
        po.setContent(msg.getContent());
        po.setMessageSeq(msg.getMessageSeq());
        return po;
    }
}
