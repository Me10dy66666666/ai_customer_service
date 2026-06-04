package com.example.backend.application.service;

import com.example.backend.domain.workorder.model.WorkOrder;
import com.example.backend.domain.workorder.repository.WorkOrderRepository;
import com.example.backend.infrastructure.persistence.entity.SlaPauseLog;
import com.example.backend.infrastructure.persistence.mapper.SlaPauseLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlaPauseService {

    private final SlaPauseLogMapper slaPauseLogMapper;
    private final WorkOrderRepository workOrderRepository;

    @Transactional
    public void pauseSla(Long workOrderId, String reason, Long operatorId) {
        WorkOrder wo = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new com.example.backend.common.exception.ResourceNotFoundException("WorkOrder not found: " + workOrderId));

        SlaPauseLog activePause = slaPauseLogMapper.findActiveByWorkOrderId(workOrderId);
        if (activePause != null) {
            log.warn("WorkOrder {} already has an active SLA pause", workOrderId);
            return;
        }

        wo.pauseSla();
        workOrderRepository.save(wo);

        SlaPauseLog pauseLog = new SlaPauseLog();
        pauseLog.setWorkOrderId(workOrderId);
        pauseLog.setPauseReason(reason);
        pauseLog.setOperatorId(operatorId);
        pauseLog.setPauseTime(LocalDateTime.now());
        pauseLog.setOriginalResponseDeadline(wo.getResponseDeadline());
        pauseLog.setOriginalSlaDeadline(wo.getSlaDeadline());
        slaPauseLogMapper.insert(pauseLog);

        log.info("SLA paused for workOrderId={}, reason={}, operatorId={}", workOrderId, reason, operatorId);
    }

    @Transactional
    public void resumeSla(Long workOrderId, Long operatorId) {
        WorkOrder wo = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new com.example.backend.common.exception.ResourceNotFoundException("WorkOrder not found: " + workOrderId));

        SlaPauseLog activePause = slaPauseLogMapper.findActiveByWorkOrderId(workOrderId);
        if (activePause == null) {
            log.warn("WorkOrder {} has no active SLA pause to resume", workOrderId);
            return;
        }

        wo.resumeSla();
        workOrderRepository.save(wo);

        long pausedSeconds = java.time.Duration.between(activePause.getPauseTime(), LocalDateTime.now()).getSeconds();
        activePause.setPausedEffectiveSeconds(pausedSeconds);
        activePause.setResumeTime(LocalDateTime.now());
        activePause.setResumeResponseDeadline(wo.getResponseDeadline());
        activePause.setResumeSlaDeadline(wo.getSlaDeadline());
        slaPauseLogMapper.updateResume(activePause);

        log.info("SLA resumed for workOrderId={}, pausedSeconds={}, operatorId={}", workOrderId, pausedSeconds, operatorId);
    }
}
