package com.example.backend.domain.workorder.model;

import com.example.backend.domain.shared.model.BaseAggregateRoot;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrder extends BaseAggregateRoot {
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String type;
    private String priority;
    private String status;
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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private boolean slaPaused;
    private Integer effectiveResponseSeconds;
    private Integer effectiveResolutionSeconds;
    private Long firstResponderId;
    private Long resolverId;
    private boolean excludeFromSla;

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_CANCELLED = "cancelled";

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public void startProcessing() {
        this.status = STATUS_PROCESSING;
        markUpdated();
    }

    public void complete(String result) {
        this.status = STATUS_COMPLETED;
        this.result = result;
        markUpdated();
    }

    public void cancel(String result) {
        this.status = STATUS_CANCELLED;
        this.result = result;
        markUpdated();
    }

    public void assignHandler(Long handlerId) {
        this.handlerId = handlerId;
        markUpdated();
    }

    public void pauseSla() {
        this.slaPaused = true;
        markUpdated();
    }

    public void resumeSla() {
        this.slaPaused = false;
        markUpdated();
    }

    public void markFirstResponder(Long agentId) {
        this.firstResponderId = agentId;
        markUpdated();
    }

    public void markResolver(Long agentId) {
        this.resolverId = agentId;
        markUpdated();
    }

    public void markExcludedFromSla() {
        this.excludeFromSla = true;
        markUpdated();
    }

    public static WorkOrder create(Long userId, String title, String description, String type, String priority) {
        WorkOrder wo = new WorkOrder();
        wo.setUserId(userId);
        wo.setTitle(title);
        wo.setDescription(description);
        wo.setType(type);
        wo.setPriority(priority != null ? priority : "medium");
        wo.setStatus(STATUS_PENDING);
        return wo;
    }
}
