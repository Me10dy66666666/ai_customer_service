package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.entity.WorkOrder;
import com.example.backend.service.WorkOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/work-orders")
@CrossOrigin(origins = "*")
public class WorkOrderController {

    @Autowired
    private WorkOrderService workOrderService;

    // H-10 提交工单
    @PostMapping
    public Result<WorkOrder> createWorkOrder(@RequestBody WorkOrder workOrder) {
        WorkOrder createdOrder = workOrderService.createWorkOrder(workOrder);
        return Result.success(createdOrder);
    }

    // H-10 查询工单列表 (支持用户ID筛选)
    @GetMapping
    public Result<List<WorkOrder>> getWorkOrders(@RequestParam(required = false) Long userId) {
        List<WorkOrder> orders;
        if (userId != null) {
            orders = workOrderService.getWorkOrdersByUserId(userId);
        } else {
            // 管理员查看所有工单 (实际生产环境应检查权限)
            orders = workOrderService.getAllWorkOrders();
        }
        return Result.success(orders);
    }

    // H-11 更新工单状态
    @PutMapping("/{id}/status")
    public Result<WorkOrder> updateWorkOrderStatus(
            @PathVariable Long id, 
            @RequestBody Map<String, Object> payload) {
        String status = (String) payload.get("status");
        Long handlerId = payload.get("handlerId") != null ? ((Number) payload.get("handlerId")).longValue() : null;
        String resultText = (String) payload.get("result");
        
        WorkOrder updatedOrder = workOrderService.updateWorkOrderStatus(id, status, handlerId, resultText);
        return Result.success(updatedOrder);
    }

    // 获取单个工单详情
    @GetMapping("/{id}")
    public Result<WorkOrder> getWorkOrder(@PathVariable Long id) {
        WorkOrder workOrder = workOrderService.getWorkOrderById(id);
        return Result.success(workOrder);
    }
}
