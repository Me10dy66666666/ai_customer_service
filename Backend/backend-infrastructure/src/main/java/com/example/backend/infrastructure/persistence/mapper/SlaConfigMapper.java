package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.SlaConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SlaConfigMapper {
    List<SlaConfig> findAllActive();
    SlaConfig findByBizTagAndPriority(@Param("bizTag") String bizTag, @Param("priority") String priority);
    int insert(SlaConfig config);
    int update(SlaConfig config);
    int deleteById(@Param("id") Long id);
    SlaConfig selectById(@Param("id") Long id);
}
