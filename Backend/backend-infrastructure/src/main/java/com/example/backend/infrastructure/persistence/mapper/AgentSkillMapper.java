package com.example.backend.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface AgentSkillMapper {
    List<Map<String, Object>> findAgentsBySkill(@Param("skillName") String skillName);
    List<Map<String, Object>> findAllActiveAgentSkills();
    List<String> findSkillsByAgentId(@Param("agentId") Long agentId);
    int insertSkill(@Param("agentId") Long agentId, @Param("skillName") String skillName);
    List<Long> findAgentIdsBySkill(@Param("skillName") String skillName);

    int deleteByAgentId(@Param("agentId") Long agentId);
}
