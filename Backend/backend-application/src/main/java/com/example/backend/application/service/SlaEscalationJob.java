package com.example.backend.application.service;

import com.example.backend.application.event.SlaAlertEvent;
import com.example.backend.domain.workorder.model.WorkOrder;
import com.example.backend.domain.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlaEscalationJob {

    private static final String SLA_REMAINING_MSG = "工单 #%d「%s」剩余 %.0f%%";
    private static final String BIZ_TAG_AFTER_SALES = "after_sales";

    private final WorkOrderRepository workOrderRepository;
    private final SlaCalculationService slaCalculationService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedRate = 30000)
    public void checkSlaEscalation() {
        List<WorkOrder> pendingOrders = workOrderRepository.findByStatus(WorkOrder.STATUS_PENDING);
        List<WorkOrder> processingOrders = workOrderRepository.findByStatus(WorkOrder.STATUS_PROCESSING);

        for (WorkOrder wo : pendingOrders) {
            checkUnclaimedEscalation(wo);
        }
        for (WorkOrder wo : processingOrders) {
            if (WorkOrder.STATUS_COMPLETED.equals(wo.getStatus())
                    || WorkOrder.STATUS_CANCELLED.equals(wo.getStatus())) {
                continue;
            }
            checkProcessingEscalation(wo);
        }
    }

    private void checkUnclaimedEscalation(WorkOrder wo) {
        if (wo.isSlaPaused() || wo.isExcludeFromSla()) return;
        if (wo.getCreateTime() == null) return;
        String bizTag = wo.getBizTag() != null ? wo.getBizTag() : mapTypeToBizTag(wo.getType());
        String priority = wo.getPriority() != null ? wo.getPriority() : "medium";
        int escalationMinutes = slaCalculationService.getEscalationMinutes(bizTag, priority);
        if (LocalDateTime.now().isAfter(wo.getCreateTime().plusMinutes(escalationMinutes))) {
            String msg = String.format("工单 #%d (%s:%s) 超过 %d 分钟未被认领", wo.getId(), bizTag, priority, escalationMinutes);
            log.warn("SLA ESCALATION: {}", msg);
            eventPublisher.publishEvent(new SlaAlertEvent(wo.getId(), wo.getTitle(), SlaAlertEvent.Level.CRITICAL, msg));
        }
    }

    private void checkProcessingEscalation(WorkOrder wo) {
        if (wo.isSlaPaused() || wo.isExcludeFromSla()) return;
        if (wo.getSlaDeadline() == null) return;
        double ratio = slaCalculationService.getSlaRemainingRatio(wo.getSlaDeadline(), wo.getCreateTime());
        SlaAlertEvent.Level level;
        String msg;
        if (ratio <= 0) {
            level = SlaAlertEvent.Level.BREACH;
            msg = String.format("工单 #%d「%s」SLA 已超时", wo.getId(), wo.getTitle());
            log.error("SLA BREACHED: {}", msg);
        } else if (ratio <= 0.15) {
            level = SlaAlertEvent.Level.CRITICAL;
            msg = String.format(SLA_REMAINING_MSG, wo.getId(), wo.getTitle(), ratio * 100);
            log.warn("SLA CRITICAL: {}", msg);
        } else if (ratio <= 0.25) {
            level = SlaAlertEvent.Level.WARNING;
            msg = String.format(SLA_REMAINING_MSG, wo.getId(), wo.getTitle(), ratio * 100);
            log.warn("SLA WARNING: {}", msg);
        } else if (ratio <= 0.50) {
            level = SlaAlertEvent.Level.NOTICE;
            msg = String.format(SLA_REMAINING_MSG, wo.getId(), wo.getTitle(), ratio * 100);
        } else {
            return;
        }
        eventPublisher.publishEvent(new SlaAlertEvent(wo.getId(), wo.getTitle(), level, msg));
    }

    private String mapTypeToBizTag(String type) {
        if (type == null) return BIZ_TAG_AFTER_SALES;
        return switch (type) {
            case "售前", "presale", "售前咨询" -> "pre_sales";
            case "售后", "after_sales", "售后服务" -> BIZ_TAG_AFTER_SALES;
            default -> BIZ_TAG_AFTER_SALES;
        };
    }
}
