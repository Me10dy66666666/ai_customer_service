package com.example.backend.infrastructure.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatMessage {
    private Long id;
    private String sessionId;
    private String senderType;
    private Long senderId;
    private String content;
    private Integer messageSeq;
    private Integer satisfaction;
    private LocalDateTime createTime;
}
