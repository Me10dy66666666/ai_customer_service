package com.example.backend.interfaces.controller;

import com.example.backend.application.service.OrderApplicationService;
import com.example.backend.common.Result;
import com.example.backend.domain.order.model.HistoricalOrder;
import com.example.backend.interfaces.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@RequireRole({"ADMIN", "AGENT"})
public class OrderController {
    private final OrderApplicationService orderApplicationService;

    @GetMapping("/user/{userId}")
    public Result<List<HistoricalOrder>> getUserOrders(@PathVariable Long userId) {
        return Result.success(orderApplicationService.getOrdersByUserId(userId));
    }

    @PostMapping("/sync/{userId}")
    public Result<List<HistoricalOrder>> syncOrders(@PathVariable Long userId) {
        return Result.success(orderApplicationService.syncOrders(userId));
    }
}
