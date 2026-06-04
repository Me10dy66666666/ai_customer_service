package com.example.backend.application.service;

import com.example.backend.infrastructure.persistence.mapper.AgentSkillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentSkillService {

    private final AgentSkillMapper agentSkillMapper;

    public List<Map<String, Object>> findAgentsBySkill(String skillName) {
        if (skillName == null || skillName.isEmpty()) {
            return Collections.emptyList();
        }
        return agentSkillMapper.findAgentsBySkill(skillName);
    }

    public List<Map<String, Object>> findAllActiveAgentSkills() {
        return agentSkillMapper.findAllActiveAgentSkills();
    }

    public List<String> getSkillsByAgentId(Long agentId) {
        return agentSkillMapper.findSkillsByAgentId(agentId);
    }

    @Transactional
    public void replaceSkills(Long agentId, List<String> skillNames) {
        agentSkillMapper.deleteByAgentId(agentId);
        if (skillNames != null) {
            for (String skillName : skillNames) {
                if (skillName != null && !skillName.isBlank()) {
                    agentSkillMapper.insertSkill(agentId, skillName.trim());
                }
            }
        }
    }
}
