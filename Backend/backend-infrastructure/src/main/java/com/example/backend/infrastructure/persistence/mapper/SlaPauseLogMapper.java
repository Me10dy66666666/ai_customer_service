package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.SlaPauseLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SlaPauseLogMapper {
    int insert(SlaPauseLog pauseLog);
    SlaPauseLog findActiveByWorkOrderId(@Param("workOrderId") Long workOrderId);
    SlaPauseLog findLatestByWorkOrderId(@Param("workOrderId") Long workOrderId);
    int updateResume(SlaPauseLog pauseLog);
}
