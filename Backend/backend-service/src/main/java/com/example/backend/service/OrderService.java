package com.example.backend.service;

import com.example.backend.entity.HistoricalOrder;
import com.example.backend.mapper.HistoricalOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final HistoricalOrderMapper orderMapper;

    public List<HistoricalOrder> getOrdersByUserId(Long userId) {
        return orderMapper.findByUserIdOrderByCreateTimeDesc(userId);
    }

    /**
     * Simulate syncing orders from an external system.
     * In MVP, we generate mock data if no orders exist for the user.
     */
    @Transactional
    public List<HistoricalOrder> syncOrders(Long userId) {
        List<HistoricalOrder> existingOrders = orderMapper.findByUserId(userId);
        
        // If user already has orders, we assume they are synced (or we could clear and re-sync)
        // For demo purposes, if empty, we generate some.
        if (existingOrders.isEmpty()) {
            return generateMockOrders(userId);
        }
        
        return existingOrders;
    }

    private List<HistoricalOrder> generateMockOrders(Long userId) {
        List<HistoricalOrder> mockOrders = new ArrayList<>();
        Random random = new Random();
        
        // Product list for randomization
        String[] products = {"智能手机 X1", "无线耳机 Pro", "智能手表 Watch 5", "笔记本电脑 Air", "平板电脑 Pad Mini"};
        String[] models = {"128GB 黑色", "白色", "运动版", "M2芯片 16G", "64GB WiFi版"};
        BigDecimal[] prices = {new BigDecimal("2999.00"), new BigDecimal("899.00"), new BigDecimal("1599.00"), new BigDecimal("8999.00"), new BigDecimal("2499.00")};
        
        // Generate 3-5 random orders
        int count = random.nextInt(3) + 3; 
        
        for (int i = 0; i < count; i++) {
            int productIndex = random.nextInt(products.length);
            
            HistoricalOrder order = new HistoricalOrder();
            order.setUserId(userId);
            order.setOrderNo("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            order.setProductName(products[productIndex]);
            order.setProductModel(models[productIndex]);
            order.setQuantity(1);
            order.setPrice(prices[productIndex]);
            order.setTotalAmount(prices[productIndex]);
            order.setOrderStatus("已完成");
            
            mockOrders.add(order);
        }
        
        for (HistoricalOrder order : mockOrders) {
            orderMapper.insert(order);
        }
        return mockOrders;
    }
}
