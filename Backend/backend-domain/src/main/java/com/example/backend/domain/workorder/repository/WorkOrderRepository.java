package com.example.backend.domain.workorder.repository;

import com.example.backend.domain.workorder.model.WorkOrder;
import java.util.List;
import java.util.Optional;

public interface WorkOrderRepository {
    WorkOrder save(WorkOrder workOrder);
    Optional<WorkOrder> findById(Long id);
    List<WorkOrder> findByUserId(Long userId);
    List<WorkOrder> findByStatus(String status);
    List<WorkOrder> findAll();
    List<WorkOrder> findUnassigned();
    List<WorkOrder> findPaginated(int offset, int limit);
    int countAll();
    int countActiveByHandlerId(Long handlerId);
    List<WorkOrder> findByHandlerOrUnassigned(Long handlerId, int offset, int limit);
    int countByHandlerOrUnassigned(Long handlerId);
    boolean claimWorkOrder(Long id, Long handlerId);
    List<WorkOrder> findBySessionId(String sessionId);
    int countActiveBySessionId(String sessionId);
}
