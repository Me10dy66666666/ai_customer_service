package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.WorkOrderTransferLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface WorkOrderTransferLogMapper {
    int insert(WorkOrderTransferLog log);
    List<WorkOrderTransferLog> findByWorkOrderId(@Param("workOrderId") Long workOrderId);
}
