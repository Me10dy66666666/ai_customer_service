package com.example.backend.domain.workorder.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderAuditLog {
    public static final String EVENT_SUBMIT = "SUBMIT";
    public static final String EVENT_AI_ANALYSIS = "AI_ANALYSIS";
    public static final String EVENT_DISPATCH = "DISPATCH";
    public static final String EVENT_STATUS_CHANGE = "STATUS_CHANGE";
    public static final String EVENT_NOTE = "NOTE";
    public static final String EVENT_COMPLETE = "COMPLETE";
    public static final String EVENT_CANCEL = "CANCEL";

    public static final String ACTOR_USER = "USER";
    public static final String ACTOR_SYSTEM = "SYSTEM";
    public static final String ACTOR_AI = "AI";
    public static final String ACTOR_AGENT = "AGENT";

    private Long id;
    private Long workOrderId;
    private String eventType;
    private String actorType;
    private Long actorId;
    private String action;
    private String detail;
    /** true=仅客服内部可见, false=用户侧可见 */
    private Boolean internalOnly;
    private LocalDateTime createTime;

    public boolean isVisibleToUser() {
        return !Boolean.TRUE.equals(internalOnly);
    }
}