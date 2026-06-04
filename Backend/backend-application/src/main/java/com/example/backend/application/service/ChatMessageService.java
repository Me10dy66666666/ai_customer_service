package com.example.backend.application.service;

import com.example.backend.domain.chat.model.ChatMessage;
import com.example.backend.domain.chat.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;

    public ChatMessage saveUser(String sessionId, Long userId, String content) {
        int seq = nextSeq(sessionId);
        ChatMessage msg = ChatMessage.user(sessionId, userId, content, seq);
        chatMessageRepository.save(msg);
        return msg;
    }

    public ChatMessage saveAgent(String sessionId, Long agentId, String content) {
        int seq = nextSeq(sessionId);
        ChatMessage msg = ChatMessage.agent(sessionId, agentId, content, seq);
        chatMessageRepository.save(msg);
        return msg;
    }

    public ChatMessage saveSystem(String sessionId, String content) {
        int seq = nextSeq(sessionId);
        ChatMessage msg = ChatMessage.system(sessionId, content, seq);
        chatMessageRepository.save(msg);
        return msg;
    }

    public List<ChatMessage> getHistory(String sessionId) {
        return chatMessageRepository.findBySessionIdOrderByMessageSeqAsc(sessionId);
    }

    int nextSeq(String sessionId) {
        return seqCache.compute(sessionId, (k, v) -> {
            if (v == null) {
                return chatMessageRepository.getMaxMessageSeqBySessionId(sessionId) + 1;
            }
            return v + 1;
        });
    }

    private final Map<String, Integer> seqCache = new ConcurrentHashMap<>();
}
