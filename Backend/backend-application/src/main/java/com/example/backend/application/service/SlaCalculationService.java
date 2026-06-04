package com.example.backend.application.service;

import com.example.backend.domain.workorder.model.WorkOrder;
import com.example.backend.infrastructure.persistence.entity.SlaConfig;
import com.example.backend.infrastructure.persistence.mapper.SlaConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlaCalculationService {

    private final SlaConfigMapper slaConfigMapper;
    private final WorkCalendarService workCalendarService;
    private final Map<String, SlaConfig> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshCache();
    }

    public void refreshCache() {
        cache.clear();
        slaConfigMapper.findAllActive().forEach(c -> {
            String key = cacheKey(c.getBizTag(), c.getPriority());
            cache.put(key, c);
        });
        log.info("SLA config cache refreshed, {} entries", cache.size());
    }

    public LocalDateTime calcSlaDeadline(String bizTag, String priority) {
        SlaConfig config = getConfig(bizTag, priority);
        if (config == null) {
            return null;
        }
        Long calendarId = getCalendarId(bizTag, priority);
        return workCalendarService.calcEffectiveDeadline(
                LocalDateTime.now(), config.getResolutionMinutes(), calendarId);
    }

    public LocalDateTime calcResponseDeadline(String bizTag, String priority) {
        SlaConfig config = getConfig(bizTag, priority);
        if (config == null) {
            return null;
        }
        Long calendarId = getCalendarId(bizTag, priority);
        return workCalendarService.calcEffectiveDeadline(
                LocalDateTime.now(), config.getResponseMinutes(), calendarId);
    }

    public void recalcDeadlineAfterResume(WorkOrder wo, long pausedEffectiveSeconds) {
        SlaConfig config = getConfig(wo.getBizTag(), wo.getPriority());
        if (config == null) {
            log.warn("No SLA config found for bizTag={}, priority={}", wo.getBizTag(), wo.getPriority());
            return;
        }
        Long calendarId = getCalendarId(wo.getBizTag(), wo.getPriority());
        int pausedMinutes = (int) (pausedEffectiveSeconds / 60L);
        if (wo.getResponseDeadline() != null && wo.getRespondedAt() == null) {
            wo.setResponseDeadline(workCalendarService.calcEffectiveDeadline(
                    wo.getResponseDeadline(), pausedMinutes, calendarId));
        }
        if (wo.getSlaDeadline() != null) {
            wo.setSlaDeadline(workCalendarService.calcEffectiveDeadline(
                    wo.getSlaDeadline(), pausedMinutes, calendarId));
        }
    }

    public void recalcDeadlineAfterPriorityChange(WorkOrder wo, String oldPriority, String newPriority) {
        log.debug("Recalculating SLA deadline after priority change for workOrder={}: {} -> {}",
                wo.getId(), oldPriority, newPriority);
        SlaConfig newConfig = getConfig(wo.getBizTag(), newPriority);
        if (newConfig == null) {
            log.warn("No new SLA config found for bizTag={}, priority={}", wo.getBizTag(), newPriority);
            return;
        }
        Long calendarId = getCalendarId(wo.getBizTag(), newPriority);
        long elapsedEffectiveSeconds = workCalendarService.calcEffectiveDuration(
                wo.getCreateTime(), LocalDateTime.now(), calendarId);
        recalcResponseDeadlineForPriorityChange(wo, newConfig, calendarId, elapsedEffectiveSeconds);
        recalcSlaDeadlineForPriorityChange(wo, newConfig, calendarId, elapsedEffectiveSeconds);
    }

    private void recalcResponseDeadlineForPriorityChange(WorkOrder wo, SlaConfig newConfig,
                                                          Long calendarId, long elapsedEffectiveSeconds) {
        if (wo.getRespondedAt() != null) {
            return;
        }
        long newResponseSeconds = newConfig.getResponseMinutes() * 60L;
        if (elapsedEffectiveSeconds >= newResponseSeconds) {
            wo.setResponseDeadline(LocalDateTime.now().minusSeconds(1L));
            log.warn("Response SLA breached due to priority change for workOrder={}", wo.getId());
            return;
        }
        long remainingSeconds = newResponseSeconds - elapsedEffectiveSeconds;
        wo.setResponseDeadline(workCalendarService.calcEffectiveDeadline(
                LocalDateTime.now(), (int) (remainingSeconds / 60L), calendarId));
    }

    private void recalcSlaDeadlineForPriorityChange(WorkOrder wo, SlaConfig newConfig,
                                                     Long calendarId, long elapsedEffectiveSeconds) {
        long newResolutionSeconds = newConfig.getResolutionMinutes() * 60L;
        if (elapsedEffectiveSeconds >= newResolutionSeconds) {
            wo.setSlaDeadline(LocalDateTime.now().minusSeconds(1L));
            log.warn("Resolution SLA breached due to priority change for workOrder={}", wo.getId());
            return;
        }
        long remainingSeconds = newResolutionSeconds - elapsedEffectiveSeconds;
        wo.setSlaDeadline(workCalendarService.calcEffectiveDeadline(
                LocalDateTime.now(), (int) (remainingSeconds / 60L), calendarId));
    }

    public double getSlaRemainingRatio(LocalDateTime slaDeadline) {
        if (slaDeadline == null) {
            return 1.0;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(slaDeadline)) {
            return 0.0;
        }
        return 1.0;
    }

    public double getSlaRemainingRatio(LocalDateTime slaDeadline, LocalDateTime createTime) {
        if (slaDeadline == null || createTime == null) {
            return 1.0;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(slaDeadline)) {
            return 0.0;
        }
        long total = java.time.Duration.between(createTime, slaDeadline).getSeconds();
        if (total <= 0L) {
            return 0.0;
        }
        long remaining = java.time.Duration.between(now, slaDeadline).getSeconds();
        return Math.clamp((double) remaining / (double) total, 0.0, 1.0);
    }

    public double getSlaRemainingRatio(LocalDateTime slaDeadline, LocalDateTime createTime,
                                        String bizTag, String priority) {
        if (slaDeadline == null || createTime == null) {
            return 1.0;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(slaDeadline)) {
            return 0.0;
        }
        Long calendarId = getCalendarId(bizTag, priority);
        long totalEffective = workCalendarService.calcEffectiveDuration(createTime, slaDeadline, calendarId);
        if (totalEffective <= 0L) {
            return 0.0;
        }
        long remainingEffective = workCalendarService.calcEffectiveDuration(now, slaDeadline, calendarId);
        return Math.clamp((double) remainingEffective / (double) totalEffective, 0.0, 1.0);
    }

    public SlaConfig getConfig(String bizTag, String priority) {
        String key = cacheKey(bizTag, priority);
        SlaConfig cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        SlaConfig fromDb = slaConfigMapper.findByBizTagAndPriority(bizTag, priority);
        if (fromDb != null) {
            cache.put(key, fromDb);
        }
        return fromDb;
    }

    public int getEscalationMinutes(String bizTag, String priority) {
        SlaConfig config = getConfig(bizTag, priority);
        return config != null ? config.getEscalationMinutes() : 15;
    }

    private Long getCalendarId(String bizTag, String priority) {
        SlaConfig config = getConfig(bizTag, priority);
        if (config != null && config.getCalendarId() != null) {
            return config.getCalendarId();
        }
        return 1L;
    }

    private String cacheKey(String bizTag, String priority) {
        return (bizTag != null ? bizTag : "null") + ":" + (priority != null ? priority : "null");
    }
}
