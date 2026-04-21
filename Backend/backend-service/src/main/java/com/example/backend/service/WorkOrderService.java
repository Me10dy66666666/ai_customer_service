package com.example.backend.service;

import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.entity.WorkOrder;
import com.example.backend.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private final WorkOrderMapper workOrderMapper;

    public WorkOrder createWorkOrder(WorkOrder workOrder) {
        if (workOrder.getStatus() == null) {
            workOrder.setStatus("pending");
        }
        if (workOrder.getPriority() == null) {
            workOrder.setPriority("medium");
        }
        if (workOrder.getId() == null) {
            workOrderMapper.insert(workOrder);
        } else {
            workOrderMapper.update(workOrder);
        }
        return workOrder;
    }

    public List<WorkOrder> getWorkOrdersByUserId(Long userId) {
        return workOrderMapper.findByUserId(userId);
    }

    public List<WorkOrder> getAllWorkOrders() {
        return workOrderMapper.selectAll();
    }

    public WorkOrder getWorkOrderById(Long id) {
        WorkOrder workOrder = workOrderMapper.selectById(id);
        if (workOrder == null) {
            throw new ResourceNotFoundException("Work order not found with id: " + id);
        }
        return workOrder;
    }

    public WorkOrder updateWorkOrderStatus(Long id, String status, Long handlerId, String result) {
        WorkOrder workOrder = getWorkOrderById(id);
        
        if (status != null && !status.isEmpty()) {
            workOrder.setStatus(status);
        }
        if (handlerId != null) {
            workOrder.setHandlerId(handlerId);
        }
        if (result != null && !result.isEmpty()) {
            workOrder.setResult(result);
        }
        workOrderMapper.update(workOrder);
        return workOrder;
    }
}
