package com.example.backend.application.service;

import com.example.backend.domain.chat.service.SessionStatePort;
import com.example.backend.domain.workorder.model.WorkOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchEngineService {

    private final AgentSkillService agentSkillService;
    private final WorkOrderApplicationService workOrderApplicationService;
    private final SessionStatePort sessionStatePort;

    public Long dispatch(WorkOrder workOrder) {
        String targetSkill = mapTypeToSkill(workOrder.getType());
        Set<Long> onlineAgentIds = sessionStatePort.getOnlineAgents();

        List<Map<String, Object>> candidates = agentSkillService.findAgentsBySkill(targetSkill);

        if (candidates.isEmpty()) {
            log.warn("[Dispatch] 工单 #{} 类型={} 映射技能={} 无精确匹配客服，进入阶梯降级",
                    workOrder.getId(), workOrder.getType(), targetSkill);
            candidates = agentSkillService.findAgentsBySkill("通用");
        }

        if (candidates.isEmpty()) {
            log.error("[Dispatch][配置故障] 工单 #{} 类型={} 技能={} 全部分类均无可用客服。"
                    + "请检查 agent_skills 表是否有种子数据，以及是否有客服在线。"
                    + "工单已进入待分配公共池 (handler_id=NULL)。",
                    workOrder.getId(), workOrder.getType(), targetSkill);
            return null;
        }

        List<Map<String, Object>> onlineCandidates = filterOnlineCandidates(candidates, onlineAgentIds);

        if (onlineCandidates.isEmpty() && !onlineAgentIds.isEmpty()) {
            log.warn("[Dispatch] 工单 #{} 匹配到 {} 个技能候选人但均不在线，"
                    + "回退到离线候选人中最空闲者",
                    workOrder.getId(), candidates.size());
            onlineCandidates = candidates;
        }

        Map<String, Object> bestCandidate = onlineCandidates.stream()
                .min(Comparator.comparingInt(c -> workOrderApplicationService
                        .countActiveByHandlerId(toLong(c.get("agent_id")))))
                .orElse(null);

        if (bestCandidate == null) {
            log.error("[Dispatch][无可用客服] 工单 #{} 类型={} 全部分类均无可用客服在线。"
                    + "工单已进入待分配公共池。",
                    workOrder.getId(), workOrder.getType());
            return null;
        }

        Long agentId = toLong(bestCandidate.get("agent_id"));
        String skill = (String) bestCandidate.get("skill_name");

        boolean claimed = workOrderApplicationService.claimWorkOrder(workOrder.getId(), agentId);
        if (claimed) {
            workOrder.setMatchingSkill(skill);
            log.info("[Dispatch] 工单 #{} 已分发 --> 客服 #{} 技能={}", workOrder.getId(), agentId, skill);
            return agentId;
        } else {
            log.warn("[Dispatch] 工单 #{} CAS认领失败(并发)，尝试降级候选人", workOrder.getId());
            return dispatchFallback(workOrder, onlineCandidates, bestCandidate);
        }
    }

    private List<Map<String, Object>> filterOnlineCandidates(List<Map<String, Object>> candidates,
                                                              Set<Long> onlineAgentIds) {
        if (onlineAgentIds.isEmpty()) return candidates;
        return candidates.stream()
                .filter(c -> {
                    Long agentId = toLong(c.get("agent_id"));
                    return agentId != null && onlineAgentIds.contains(agentId);
                })
                .collect(Collectors.toList());
    }

    private Long dispatchFallback(WorkOrder workOrder, List<Map<String, Object>> candidates,
                                   Map<String, Object> excludeCandidate) {
        for (Map<String, Object> c : candidates) {
            if (c.equals(excludeCandidate)) continue;
            Long agentId = toLong(c.get("agent_id"));
            if (workOrderApplicationService.claimWorkOrder(workOrder.getId(), agentId)) {
                workOrder.setMatchingSkill((String) c.get("skill_name"));
                log.info("[Dispatch] 工单 #{} 降级分发 --> 客服 #{}", workOrder.getId(), agentId);
                return agentId;
            }
        }
        log.error("[Dispatch] 工单 #{} 降级分发全部失败，工单进入待分配公共池", workOrder.getId());
        return null;
    }

    private String mapTypeToSkill(String type) {
        if (type == null) return "通用";
        return switch (type) {
            case "presale", "售前", "售前咨询" -> "售前";
            case "after_sales", "售后", "售后服务" -> "售后";
            default -> "通用";
        };
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }
}
