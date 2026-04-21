package com.example.backend.mapper;

import com.example.backend.entity.WorkOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface WorkOrderMapper {
    int insert(WorkOrder workOrder);
    int update(WorkOrder workOrder);
    int deleteById(Long id);
    WorkOrder selectById(Long id);
    List<WorkOrder> selectAll();

    List<WorkOrder> findByUserId(Long userId);
    List<WorkOrder> findByStatus(String status);
    List<WorkOrder> findByHandlerId(Long handlerId);

    List<Map<String, Object>> countByStatusBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    List<Map<String, Object>> countWorkOrderTrendByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
