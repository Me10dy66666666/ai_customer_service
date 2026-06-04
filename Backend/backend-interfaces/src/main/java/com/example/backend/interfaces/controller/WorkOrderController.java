package com.example.backend.interfaces.controller;

import com.example.backend.application.dto.CreateWorkOrderCommand;
import com.example.backend.application.dto.ReplyWorkOrderCommand;
import com.example.backend.application.dto.TransferWorkOrderCommand;
import com.example.backend.application.dto.UpdateWorkOrderCommand;
import com.example.backend.application.service.ChatMessageService;
import com.example.backend.application.service.ChatSummaryService;
import com.example.backend.application.service.DispatchEngineService;
import com.example.backend.application.service.SlaCalculationService;
import com.example.backend.application.service.WorkOrderApplicationService;
import com.example.backend.common.Result;
import com.example.backend.common.enums.SlaPauseReason;
import com.example.backend.domain.chat.model.ChatMessage;
import com.example.backend.domain.workorder.model.WorkOrder;
import com.example.backend.domain.workorder.model.WorkOrderAuditLog;
import com.example.backend.infrastructure.messaging.MessageRouter;
import com.example.backend.infrastructure.persistence.entity.User;
import com.example.backend.infrastructure.persistence.mapper.UserMapper;
import com.example.backend.interfaces.websocket.ChatWebSocketHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {
    private final WorkOrderApplicationService workOrderApplicationService;
    private final ChatSummaryService chatSummaryService;
    private final DispatchEngineService dispatchEngineService;
    private final ChatWebSocketHandler chatWebSocketHandler;
    private final SlaCalculationService slaCalculationService;
    private final UserMapper userMapper;
    private final MessageRouter messageRouter;
    private final ChatMessageService chatMessageService;

    private static final String KEY_WORK_ORDER_ID = "workOrderId";
    private static final String KEY_CLAIMED = "claimed";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_SESSION_ID = "sessionId";

    @PostMapping
    public Result<WorkOrder> create(@Valid @RequestBody CreateWorkOrderCommand command) {
        WorkOrder wo = WorkOrder.create(
                command.getUserId(),
                command.getTitle(),
                command.getDescription(),
                command.getType(),
                command.getPriority());

        User user = userMapper.selectById(command.getUserId());
        if (user != null) {
            wo.setUserPhone(user.getPhone());
            wo.setUserNickname(user.getNickname() != null ? user.getNickname() : user.getUsername());
        }

        String resolvedSessionId = resolveSessionId(command.getSessionId());
        wo.setSessionId(resolvedSessionId);

        String initialBizTag = command.getType() != null && (command.getType().contains("售前") || "presale".equalsIgnoreCase(command.getType()))
                ? "pre_sales" : "after_sales";
        wo.setSlaDeadline(slaCalculationService.calcSlaDeadline(initialBizTag, command.getPriority()));
        wo.setResponseDeadline(slaCalculationService.calcResponseDeadline(initialBizTag, command.getPriority()));

        WorkOrder saved = workOrderApplicationService.createWorkOrder(wo, command.getCreatorAgentId());

        String effectiveSessionId = resolvedSessionId;
        if (effectiveSessionId != null && !effectiveSessionId.isEmpty()) {
            chatWebSocketHandler.sendToUser(effectiveSessionId, Map.of(
                    "type", "workorder_created",
                    KEY_WORK_ORDER_ID, saved.getId(),
                    "title", saved.getTitle(),
                    "description", saved.getDescription(),
                    "woType", saved.getType(),
                    "status", saved.getStatus(),
                    "createTime", saved.getCreateTime() != null ? saved.getCreateTime().toString() : ""
            ));
        }

        chatSummaryService.summarizeWorkorder(
                effectiveSessionId != null ? effectiveSessionId : saved.getId().toString(),
                saved.getUserId(),
                command.getTitle(),
                command.getType(),
                command.getDescription(),
                saved.getCreateTime(),
                analysisResult -> {
                    workOrderApplicationService.updateTagsAndPriority(
                            saved.getId(), analysisResult);
                    pushSummaryReady(saved.getId(), analysisResult);
                });

        try {
            dispatchEngineService.dispatch(saved);
        } catch (Exception e) {
            log.warn("WorkOrder {} dispatch failed: {}", saved.getId(), e.getMessage());
        }

        return Result.success(saved);
    }

    private String resolveSessionId(String originalSessionId) {
        return (originalSessionId == null || originalSessionId.isEmpty()) ? null : originalSessionId;
    }

    private void pushSummaryReady(Long workOrderId,
                                   com.example.backend.infrastructure.dify.WorkOrderAnalysisResult analysisResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "SUMMARY_READY");
        payload.put(KEY_WORK_ORDER_ID, workOrderId);
        if (analysisResult.getPriority() != null) payload.put("priority", analysisResult.getPriority());
        if (analysisResult.getTags() != null) payload.put("tags", analysisResult.getTags());
        if (analysisResult.getSummary() != null) payload.put("summary", analysisResult.getSummary());
        if (analysisResult.getBizTag() != null) payload.put("bizTag", analysisResult.getBizTag());
        if (analysisResult.getEmotionLevel() != null) payload.put("emotionLevel", analysisResult.getEmotionLevel());
        if (analysisResult.getDispatchConfidence() != null) payload.put("dispatchConfidence", analysisResult.getDispatchConfidence());
        chatWebSocketHandler.broadcastToAgents(payload);
    }

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(required = false) Long userId,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        if (userId != null) {
            return Result.success(Map.of("list", workOrderApplicationService.findByUserId(userId), "total", workOrderApplicationService.findByUserId(userId).size()));
        }
        int offset = (page - 1) * size;
        return Result.success(Map.of(
                "list", workOrderApplicationService.findPaginated(offset, size),
                "total", workOrderApplicationService.countAll(),
                "page", page,
                "size", size
        ));
    }

    @GetMapping("/{id}")
    public Result<WorkOrder> getById(@PathVariable Long id) {
        return Result.success(workOrderApplicationService.findById(id));
    }

    @GetMapping("/unassigned")
    public Result<List<WorkOrder>> listUnassigned() {
        return Result.success(workOrderApplicationService.findUnassigned());
    }

    @PutMapping("/{id}/status")
    public Result<WorkOrder> updateStatus(@PathVariable Long id,
                                           @Valid @RequestBody UpdateWorkOrderCommand command) {
        WorkOrder saved = workOrderApplicationService.updateStatus(id,
                Map.of("status", command.getStatus(),
                        "handlerId", command.getHandlerId() != null ? command.getHandlerId() : "",
                        "result", command.getResult() != null ? command.getResult() : ""));
        return Result.success(saved);
    }

    @PostMapping("/{id}/claim")
    public Result<Map<String, Object>> claim(@PathVariable Long id,
                                              @RequestParam Long handlerId) {
        boolean claimed = workOrderApplicationService.claimWorkOrder(id, handlerId);
        if (claimed) {
            return Result.success(Map.of(KEY_CLAIMED, true, "message", "工单认领成功"));
        }
        return Result.success(Map.of(KEY_CLAIMED, false, "message", "工单已被其他客服认领"));
    }

    @PostMapping("/{id}/reply")
    public Result<Map<String, Object>> reply(@PathVariable Long id,
                                              @Valid @RequestBody ReplyWorkOrderCommand command) {
        ChatMessage message = workOrderApplicationService.replyWorkOrder(id, command.getContent(), command.getAgentId());
        WorkOrder wo = workOrderApplicationService.findById(id);

        Map<String, Object> replyPush = new LinkedHashMap<>();
        replyPush.put("type", "workorder_reply");
        replyPush.put(KEY_WORK_ORDER_ID, id);
        replyPush.put(KEY_CONTENT, command.getContent());
        replyPush.put("result", wo.getResult());
        replyPush.put(KEY_SESSION_ID, message.getSessionId());
        chatWebSocketHandler.sendToUser(message.getSessionId(), replyPush);

        Map<String, Object> chatPush = new LinkedHashMap<>();
        chatPush.put("type", "agent_msg");
        chatPush.put(KEY_CONTENT, command.getContent());
        chatWebSocketHandler.sendToUser(message.getSessionId(), chatPush);

        return Result.success(Map.of("messageId", message.getId(), KEY_SESSION_ID, message.getSessionId()));
    }

    @PostMapping("/{id}/transfer")
    public Result<WorkOrder> transfer(@PathVariable Long id,
                                       @Valid @RequestBody TransferWorkOrderCommand command) {
        WorkOrder updated = workOrderApplicationService.transferWorkOrder(id, command.getTargetHandlerId());
        return Result.success(updated);
    }

    @PostMapping("/{id}/note")
    public Result<Map<String, Object>> addNote(@PathVariable Long id,
                                                @RequestBody Map<String, String> body) {
        String content = body.get(KEY_CONTENT);
        if (content == null || content.trim().isEmpty()) {
            return Result.error(400, "备注内容不能为空");
        }
        Long agentId = null;
        try { agentId = Long.parseLong(body.getOrDefault("agentId", "0")); } catch (NumberFormatException ignored) { }
        WorkOrderAuditLog log = workOrderApplicationService.addNote(id, content, agentId);
        return Result.success(Map.of("id", log.getId(), KEY_WORK_ORDER_ID, log.getWorkOrderId(),
                "action", log.getAction(), "detail", log.getDetail(),
                "createTime", log.getCreateTime() != null ? log.getCreateTime().toString() : ""));
    }

    @PostMapping("/{id}/rate")
    public Result<?> rateWorkOrder(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer rating = body.get("rating") != null ? Integer.valueOf(body.get("rating").toString()) : null;
        Long userId = body.get("userId") != null ? Long.valueOf(body.get("userId").toString()) : null;
        if (rating == null || rating < 1 || rating > 5) {
            return Result.error(400, "评分必须在1-5之间");
        }
        workOrderApplicationService.rateWorkOrder(id, userId, rating);
        return Result.success();
    }

    @GetMapping("/{id}/audit-logs")
    public Result<List<WorkOrderAuditLog>> getAuditLogs(@PathVariable Long id,
                                                          @RequestParam(defaultValue = "false") boolean userVisible) {
        return Result.success(workOrderApplicationService.getAuditLogs(id, userVisible));
    }

    @PostMapping("/{id}/connect-session")
    public Result<Map<String, Object>> connectSession(@PathVariable Long id,
                                                       @RequestParam Long agentId) {
        boolean freshClaimed = workOrderApplicationService.claimWorkOrder(id, agentId);
        WorkOrder wo = workOrderApplicationService.findById(id);
        // 工单已被当前客服认领时仍应允许建立会话沟通
        boolean alreadyClaimed = wo.getHandlerId() != null && wo.getHandlerId().equals(agentId);
        if ((!freshClaimed && !alreadyClaimed) || wo.getSessionId() == null) {
            return Result.success(Map.of(KEY_CLAIMED, false));
        }
        boolean sessionClaimed = messageRouter.claimSession(wo.getSessionId(), agentId);
        if (sessionClaimed || messageRouter.isHumanSession(wo.getSessionId())) {
            chatMessageService.saveSystem(wo.getSessionId(), "客服已接入，回复将同步到工单");
            boolean sent = chatWebSocketHandler.sendToUser(wo.getSessionId(), Map.of(
                    "type", "agent_joined",
                    KEY_SESSION_ID, wo.getSessionId(),
                    "agentId", agentId));
            if (!sent) {
                log.warn("Session {} claimed by agent {} but user WebSocket not connected, " +
                        "agent_joined message queued to Redis stream", wo.getSessionId(), agentId);
            }
        }
        return Result.success(Map.of(KEY_CLAIMED, true));
    }

    @GetMapping("/{id}/user-phone")
    public Result<Map<String, String>> getUserPhone(@PathVariable Long id,
                                                      @RequestParam Long agentId) {
        WorkOrder wo = workOrderApplicationService.findById(id);
        if (wo == null || wo.getUserId() == null) {
            return Result.error(404, "工单不存在或用户信息缺失");
        }
        User user = userMapper.selectById(wo.getUserId());
        String phone = user != null ? user.getPhone() : null;
        
        workOrderApplicationService.addNote(id,
                "客服#" + agentId + " 查看了用户(" + wo.getUserId() + ")的手机号", agentId);
        log.info("AUDIT: agent {} viewed phone of user {} via work order {}", agentId, wo.getUserId(), id);
        return Result.success(Map.of("phone", phone != null ? phone : ""));
    }

    @PostMapping("/{id}/close-session")
    public Result<Map<String, Object>> closeSession(@PathVariable Long id,
                                                     @RequestParam Long agentId) {
        WorkOrder wo = workOrderApplicationService.findById(id);
        if (wo == null || wo.getSessionId() == null) {
            return Result.error(400, "工单不存在或会话未关联");
        }
        try {
            messageRouter.closeSession(wo.getSessionId());
            chatWebSocketHandler.sendToUser(wo.getSessionId(), Map.of(
                    "type", "back_to_ai",
                    KEY_CONTENT, "人工服务已结束，已切换回AI服务"));
            chatMessageService.saveSystem(wo.getSessionId(), "客服已结束本次服务");
            workOrderApplicationService.addNote(id,
                    "客服#" + agentId + " 关闭了会话", agentId);
        } catch (Exception e) {
            log.error("Failed to close session {} for work order {}", wo.getSessionId(), id, e);
            return Result.error(500, "关闭会话失败，请稍后重试");
        }
        return Result.success(Map.of("closed", true));
    }

    @PostMapping("/{id}/pause-sla")
    public Result<Map<String, Object>> pauseSla(@PathVariable Long id,
                                                 @RequestBody Map<String, String> body) {
        String reason = body.get("reason");
        if (reason == null || !SlaPauseReason.isValid(reason)) {
            return Result.error(400, "无效的暂停原因: " + reason + "，有效值: " + SlaPauseReason.getValidCodes());
        }
        Long agentIdLong = null;
        String agentIdStr = body.get("agentId");
        if (agentIdStr != null && !agentIdStr.isEmpty()) {
            try {
                agentIdLong = Long.parseLong(agentIdStr);
            } catch (NumberFormatException e) {
                return Result.error(400, "无效的agentId");
            }
        }
        try {
            workOrderApplicationService.pauseSla(id, reason, agentIdLong);
            return Result.success(Map.of("paused", true));
        } catch (Exception e) {
            log.error("Failed to pause SLA for work order {}: {}", id, e.getMessage(), e);
            return Result.error(500, "暂停SLA失败");
        }
    }

    @PostMapping("/{id}/resume-sla")
    public Result<Map<String, Object>> resumeSla(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> body) {
        Long agentIdLong = null;
        Object agentIdObj = body.get("agentId");
        if (agentIdObj != null) {
            try {
                if (agentIdObj instanceof Number) {
                    agentIdLong = ((Number) agentIdObj).longValue();
                } else {
                    agentIdLong = Long.parseLong(agentIdObj.toString());
                }
            } catch (NumberFormatException e) {
                return Result.error(400, "无效的agentId");
            }
        }
        try {
            workOrderApplicationService.resumeSla(id, agentIdLong);
            WorkOrder updatedWo = workOrderApplicationService.findById(id);
            Map<String, Object> resumeResult = new LinkedHashMap<>();
            resumeResult.put("resumed", true);
            resumeResult.put("responseDeadline", updatedWo.getResponseDeadline() != null ? updatedWo.getResponseDeadline().toString() : null);
            resumeResult.put("slaDeadline", updatedWo.getSlaDeadline() != null ? updatedWo.getSlaDeadline().toString() : null);
            return Result.success(resumeResult);
        } catch (Exception e) {
            log.error("Failed to resume SLA for work order {}: {}", id, e.getMessage(), e);
            return Result.error(500, "恢复SLA失败");
        }
    }
}
