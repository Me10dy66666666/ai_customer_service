package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.entity.HistoricalOrder;
import com.example.backend.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*") // Allow frontend access
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * Get historical orders for a specific user.
     * In a real system, userId would be extracted from the JWT token.
     * For MVP, we allow passing it as a parameter or path variable for simplicity/testing.
     */
    @GetMapping("/user/{userId}")
    public Result<List<HistoricalOrder>> getUserOrders(@PathVariable Long userId) {
        List<HistoricalOrder> orders = orderService.getOrdersByUserId(userId);
        return Result.success(orders);
    }

    /**
     * Trigger sync of historical orders for a user.
     * This simulates fetching from an external ERP/Order system.
     */
    @PostMapping("/sync/{userId}")
    public Result<List<HistoricalOrder>> syncUserOrders(@PathVariable Long userId) {
        List<HistoricalOrder> orders = orderService.syncOrders(userId);
        return Result.success(orders);
    }
}
