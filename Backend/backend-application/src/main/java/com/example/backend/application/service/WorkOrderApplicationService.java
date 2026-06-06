package com.example.backend.application.service;

import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.domain.chat.model.ChatMessage;
import com.example.backend.domain.chat.repository.ChatMessageRepository;
import com.example.backend.domain.workorder.event.WorkOrderCreatedEvent;
import com.example.backend.domain.workorder.model.WorkOrder;
import com.example.backend.domain.workorder.model.WorkOrderAuditLog;
import com.example.backend.domain.workorder.repository.WorkOrderRepository;
import com.example.backend.domain.shared.event.DomainEventPublisher;
import com.example.backend.infrastructure.dify.WorkOrderAnalysisResult;
import com.example.backend.infrastructure.persistence.entity.WorkOrderAuditLogEntity;
import com.example.backend.infrastructure.persistence.entity.WorkOrderTransferLog;
import com.example.backend.infrastructure.persistence.entity.SlaConfig;
import com.example.backend.infrastructure.persistence.mapper.WorkOrderAuditLogMapper;
import com.example.backend.infrastructure.persistence.mapper.WorkOrderMapper;
import com.example.backend.infrastructure.persistence.mapper.WorkOrderTransferLogMapper;
import com.example.backend.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderApplicationService {

    private static final String KEY_HANDLER_ID = "handlerId";

    private final WorkOrderRepository workOrderRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DomainEventPublisher eventPublisher;
    private final WorkOrderTransferLogMapper transferLogMapper;
    private final WorkOrderAuditLogMapper auditLogMapper;
    private final SlaPauseService slaPauseService;
    private final SlaCalculationService slaCalculationService;
    private final WorkCalendarService workCalendarService;
    private final WorkOrderMapper workOrderMapper;

    @Transactional
    public WorkOrder createWorkOrder(WorkOrder workOrder, Long creatorAgentId) {
        WorkOrder saved = workOrderRepository.save(workOrder);
        if (creatorAgentId != null) {
            recordAudit(saved.getId(), WorkOrderAuditLog.EVENT_SUBMIT,
                    WorkOrderAuditLog.ACTOR_AGENT, creatorAgentId,
                    "客服创建工单", workOrder.getDescription(), false);
        } else {
            recordAudit(saved.getId(), WorkOrderAuditLog.EVENT_SUBMIT,
                    WorkOrderAuditLog.ACTOR_USER, saved.getUserId(),
                    "用户提交工单", workOrder.getDescription(), false);
        }
        eventPublisher.publish(new WorkOrderCreatedEvent(saved.getId(), saved.getUserId(),
                saved.getType(), saved.getPriority()));
        return saved;
    }

    public List<WorkOrder> findByUserId(Long userId) {
        return workOrderRepository.findByUserId(userId);
    }

    public List<WorkOrder> findAll() {
        return workOrderRepository.findAll();
    }

    public List<WorkOrder> findPaginated(int offset, int limit) {
        return workOrderRepository.findPaginated(offset, limit);
    }

    public int countAll() {
        return workOrderRepository.countAll();
    }

    public List<WorkOrder> findByHandlerOrUnassigned(Long handlerId, int offset, int limit) {
        return workOrderRepository.findByHandlerOrUnassigned(handlerId, offset, limit);
    }

    public int countByHandlerOrUnassigned(Long handlerId) {
        return workOrderRepository.countByHandlerOrUnassigned(handlerId);
    }

    public WorkOrder findById(Long id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrder not found: " + id));
    }

    @Transactional
    public WorkOrder updateStatus(Long id, Map<String, Object> payload) {
        WorkOrder wo = findById(id);
        String oldStatus = wo.getStatus();
        String newStatus = oldStatus;

        if (payload.containsKey("status")) {
            newStatus = String.valueOf(payload.get("status"));
            switch (newStatus) {
                case WorkOrder.STATUS_PROCESSING:
                    wo.startProcessing();
                    break;
                case WorkOrder.STATUS_COMPLETED:
                    wo.complete(payload.getOrDefault("result", "").toString());
                    Long resolverId = payload.containsKey(KEY_HANDLER_ID)
                            && payload.get(KEY_HANDLER_ID) instanceof Number num
                            ? num.longValue() : wo.getHandlerId();
                    wo.markResolver(resolverId);
                    long effSeconds = workCalendarService.calcEffectiveDuration(
                            wo.getCreateTime(), LocalDateTime.now(), getCalendarId(wo));
                    wo.setEffectiveResolutionSeconds((int) effSeconds);
                    break;
                case WorkOrder.STATUS_CANCELLED:
                    wo.cancel(payload.getOrDefault("result", "").toString());
                    break;
                default:
                    break;
            }
        }
        if (payload.containsKey(KEY_HANDLER_ID)) {
            Object hid = payload.get(KEY_HANDLER_ID);
            if (hid instanceof Number n) wo.assignHandler(n.longValue());
        }

        WorkOrder saved = workOrderRepository.save(wo);

        if (!Objects.equals(oldStatus, newStatus)) {
            String statusLabel = getStatusLabel(newStatus);
            Long actorId = payload.containsKey(KEY_HANDLER_ID) && payload.get(KEY_HANDLER_ID) instanceof Number num
                    ? num.longValue() : null;
            recordAudit(saved.getId(), WorkOrderAuditLog.EVENT_STATUS_CHANGE,
                    WorkOrderAuditLog.ACTOR_AGENT, actorId,
                    "状态变更", oldStatus + " → " + statusLabel, false);
        }

        return saved;
    }

    @Transactional
    public void updateTagsAndPriority(Long id, WorkOrderAnalysisResult analysisResult) {
        WorkOrder wo = findById(id);
        String oldPriority = wo.getPriority();
        applyAnalysisResult(wo, analysisResult);
        if (analysisResult.getPriority() != null && !analysisResult.getPriority().isEmpty()
                && oldPriority != null && !oldPriority.equals(analysisResult.getPriority())) {
            slaCalculationService.recalcDeadlineAfterPriorityChange(
                    wo, oldPriority, analysisResult.getPriority());
        }
        workOrderRepository.save(wo);

        recordAudit(id, WorkOrderAuditLog.EVENT_AI_ANALYSIS,
                WorkOrderAuditLog.ACTOR_AI, null,
                "AI 分析完成",
                "优先级:" + (analysisResult.getPriority() != null ? analysisResult.getPriority() : "-")
                        + " 摘要:" + (analysisResult.getSummary() != null ? analysisResult.getSummary() : ""),
                true);
    }

    private void applyAnalysisResult(WorkOrder wo, WorkOrderAnalysisResult result) {
        if (result.getPriority() != null && !result.getPriority().isEmpty()) {
            wo.setPriority(result.getPriority());
        }
        if (result.getTags() != null && !result.getTags().isEmpty()) {
            wo.setTags(result.getTags());
        }
        if (result.getSummary() != null && !result.getSummary().isEmpty()) {
            wo.setSummary(result.getSummary());
        }
        if (result.getDispatchConfidence() != null) {
            wo.setDispatchConfidence(result.getDispatchConfidence());
        }
        if (result.getBizTag() != null && !result.getBizTag().isEmpty()) {
            wo.setBizTag(result.getBizTag());
        }
        if (result.getEmotionLevel() != null && !result.getEmotionLevel().isEmpty()) {
            wo.setEmotionLevel(result.getEmotionLevel());
        }
    }

    public List<WorkOrder> findUnassigned() {
        return workOrderRepository.findUnassigned();
    }

    public int countActiveByHandlerId(Long handlerId) {
        return workOrderRepository.countActiveByHandlerId(handlerId);
    }

    public int countActiveBySessionId(String sessionId) {
        return workOrderRepository.countActiveBySessionId(sessionId);
    }

    @Transactional
    public boolean claimWorkOrder(Long id, Long handlerId) {
        boolean claimed = workOrderRepository.claimWorkOrder(id, handlerId);
        if (claimed) {
            recordAudit(id, WorkOrderAuditLog.EVENT_DISPATCH,
                    WorkOrderAuditLog.ACTOR_SYSTEM, null,
                    "系统分派", "负责人绑定为: " + handlerId, false);
            recordAudit(id, WorkOrderAuditLog.EVENT_STATUS_CHANGE,
                    WorkOrderAuditLog.ACTOR_AGENT, handlerId,
                    "状态变更", "待处理 → 处理中", false);
        }
        return claimed;
    }

    @Transactional
    public WorkOrderAuditLog addNote(Long workOrderId, String content, Long agentId) {
        return recordAudit(workOrderId, WorkOrderAuditLog.EVENT_NOTE,
                WorkOrderAuditLog.ACTOR_AGENT, agentId,
                "客服备注", content, true);
    }

    /**
     * 用户对工单进行评价。
     *
     * @param workOrderId 工单ID
     * @param userId 用户ID（用于权限校验）
     * @param rating 评分 1-5
     */
    @Transactional
    public void rateWorkOrder(Long workOrderId, Long userId, Integer rating) {
        com.example.backend.infrastructure.persistence.entity.WorkOrder wo = workOrderMapper.selectById(workOrderId);
        if (wo == null) {
            throw new BusinessException("工单不存在");
        }
        if (!"completed".equals(wo.getStatus())) {
            throw new BusinessException("仅可对已完成的工单进行评价");
        }
        if (wo.getUserId() != null && !wo.getUserId().equals(userId)) {
            throw new BusinessException("无权评价此工单");
        }
        wo.setRating(rating);
        workOrderMapper.update(wo);
    }

    public List<WorkOrderAuditLog> getAuditLogs(Long workOrderId, boolean userVisibleOnly) {
        List<WorkOrderAuditLogEntity> entities;
        if (userVisibleOnly) {
            entities = auditLogMapper.findUserVisibleByWorkOrderId(workOrderId);
        } else {
            entities = auditLogMapper.findAllByWorkOrderId(workOrderId);
        }
        if (entities == null) return List.of();
        return entities.stream().map(e -> WorkOrderAuditLog.builder()
                .id(e.getId())
                .workOrderId(e.getWorkOrderId())
                .eventType(e.getEventType())
                .actorType(e.getActorType())
                .actorId(e.getActorId())
                .action(e.getAction())
                .detail(e.getDetail())
                .internalOnly(e.getInternalOnly())
                .createTime(e.getCreateTime())
                .build()).toList();
    }

    @Transactional
    public void recordDispatch(Long workOrderId, Long handlerId) {
        recordAudit(workOrderId, WorkOrderAuditLog.EVENT_DISPATCH,
                WorkOrderAuditLog.ACTOR_SYSTEM, null,
                "系统分派", "自动匹配 → 客服#" + handlerId, false);
    }

    private WorkOrderAuditLog recordAudit(Long workOrderId, String eventType,
                                           String actorType, Long actorId,
                                           String action, String detail, boolean internalOnly) {
        WorkOrderAuditLogEntity entity = new WorkOrderAuditLogEntity();
        entity.setWorkOrderId(workOrderId);
        entity.setEventType(eventType);
        entity.setActorType(actorType);
        entity.setActorId(actorId);
        entity.setAction(action);
        entity.setDetail(detail);
        entity.setInternalOnly(internalOnly);
        entity.setCreateTime(LocalDateTime.now());
        auditLogMapper.insert(entity);

        return WorkOrderAuditLog.builder()
                .id(entity.getId())
                .workOrderId(workOrderId)
                .eventType(eventType)
                .actorType(actorType)
                .actorId(actorId)
                .action(action)
                .detail(detail)
                .internalOnly(internalOnly)
                .createTime(entity.getCreateTime())
                .build();
    }

    @SuppressWarnings("unused")
    @Transactional
    public ChatMessage replyWorkOrder(Long workOrderId, String content, Long agentId) {
        WorkOrder wo = findById(workOrderId);
        if (wo.getSessionId() == null || wo.getSessionId().isEmpty()) {
            throw new ResourceNotFoundException("工单 " + workOrderId + " 未关联会话，无法通过聊天回复");
        }
        if (wo.getRespondedAt() == null) {
            wo.markFirstResponder(agentId);
            wo.setRespondedAt(LocalDateTime.now());
            long effSeconds = workCalendarService.calcEffectiveDuration(
                    wo.getCreateTime(), wo.getRespondedAt(), getCalendarId(wo));
            wo.setEffectiveResponseSeconds((int) effSeconds);
        }
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
                .format(LocalDateTime.now());
        String existingResult = wo.getResult() != null ? wo.getResult() : "";
        String appendedResult = existingResult.isEmpty()
                ? "[客服回复 " + timestamp + "] " + content
                : existingResult + "\n[客服回复 " + timestamp + "] " + content;
        wo.setResult(appendedResult);
        workOrderRepository.save(wo);

        Integer nextSeq = chatMessageRepository.getMaxMessageSeqBySessionId(wo.getSessionId());
        ChatMessage message = ChatMessage.agent(wo.getSessionId(), agentId, content, nextSeq + 1);
        return chatMessageRepository.save(message);
    }

    @Transactional
    public WorkOrder transferWorkOrder(Long workOrderId, Long targetHandlerId) {
        WorkOrder wo = findById(workOrderId);
        Long previousHandlerId = wo.getHandlerId();
        wo.assignHandler(targetHandlerId);
        if (!WorkOrder.STATUS_PROCESSING.equals(wo.getStatus())
                && !WorkOrder.STATUS_COMPLETED.equals(wo.getStatus())) {
            wo.startProcessing();
        }
        WorkOrder saved = workOrderRepository.save(wo);

        WorkOrderTransferLog transferLog = new WorkOrderTransferLog();
        transferLog.setWorkOrderId(workOrderId);
        transferLog.setFromHandlerId(previousHandlerId);
        transferLog.setToHandlerId(targetHandlerId);
        transferLogMapper.insert(transferLog);

        recordAudit(workOrderId, WorkOrderAuditLog.EVENT_DISPATCH,
                WorkOrderAuditLog.ACTOR_SYSTEM, null,
                "转移工单", "客服#" + previousHandlerId + " → 客服#" + targetHandlerId, false);

        return saved;
    }

    @Transactional
    public void pauseSla(Long workOrderId, String reason, Long operatorId) {
        slaPauseService.pauseSla(workOrderId, reason, operatorId);
    }

    @Transactional
    public void resumeSla(Long workOrderId, Long operatorId) {
        slaPauseService.resumeSla(workOrderId, operatorId);
    }

    private Long getCalendarId(WorkOrder wo) {
        SlaConfig config = slaCalculationService.getConfig(wo.getBizTag(), wo.getPriority());
        return config != null && config.getCalendarId() != null ? config.getCalendarId() : 1L;
    }

    private String getStatusLabel(String status) {
        return switch (status) {
            case WorkOrder.STATUS_PENDING -> "待处理";
            case WorkOrder.STATUS_PROCESSING -> "处理中";
            case WorkOrder.STATUS_COMPLETED -> "已完成";
            case WorkOrder.STATUS_CANCELLED -> "已取消";
            default -> status;
        };
    }
}
