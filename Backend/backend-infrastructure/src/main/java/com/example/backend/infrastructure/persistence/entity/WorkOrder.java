package com.example.backend.infrastructure.persistence.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WorkOrder {
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String type;
    private String priority = "medium";
    private String status = "pending";
    private Long handlerId;
    private String userPhone;
    private String userNickname;
    private String result;
    private String tags;
    private String summary;
    private String sessionId;
    private String matchingSkill;
    private BigDecimal dispatchConfidence;
    private String bizTag;
    private String emotionLevel;
    private LocalDateTime slaDeadline;
    private LocalDateTime responseDeadline;
    private LocalDateTime respondedAt;
    private Integer slaPaused;
    private Integer effectiveResponseSeconds;
    private Integer effectiveResolutionSeconds;
    private Long firstResponderId;
    private Long resolverId;
    private Integer excludeFromSla;
    /** 工单评价 1-5星，NULL=未评价 */
    private Integer rating;
    private Long lockVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
