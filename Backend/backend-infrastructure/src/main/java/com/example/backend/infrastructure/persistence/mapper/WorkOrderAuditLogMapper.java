package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.WorkOrderAuditLogEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface WorkOrderAuditLogMapper {

    @Insert("INSERT INTO work_order_audit_log (work_order_id, event_type, actor_type, actor_id, action, detail, internal_only, create_time) " +
            "VALUES (#{workOrderId}, #{eventType}, #{actorType}, #{actorId}, #{action}, #{detail}, #{internalOnly}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WorkOrderAuditLogEntity entity);

    @Select("SELECT * FROM work_order_audit_log WHERE work_order_id = #{workOrderId} AND internal_only = 0 ORDER BY create_time ASC")
    List<WorkOrderAuditLogEntity> findUserVisibleByWorkOrderId(@Param("workOrderId") Long workOrderId);

    @Select("SELECT * FROM work_order_audit_log WHERE work_order_id = #{workOrderId} ORDER BY create_time ASC")
    List<WorkOrderAuditLogEntity> findAllByWorkOrderId(@Param("workOrderId") Long workOrderId);
}