package com.example.backend.application.service;

import com.example.backend.domain.order.event.OrdersSyncedEvent;
import com.example.backend.domain.order.model.HistoricalOrder;
import com.example.backend.domain.order.repository.HistoricalOrderRepository;
import com.example.backend.domain.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderApplicationService {
    private final HistoricalOrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;

    public List<HistoricalOrder> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByCreateTimeDesc(userId);
    }

    @Transactional
    public List<HistoricalOrder> syncOrders(Long userId) {
        List<HistoricalOrder> existing = orderRepository.findByUserId(userId);
        if (existing.isEmpty()) {
            List<HistoricalOrder> generated = generateMockOrders(userId);
            eventPublisher.publish(new OrdersSyncedEvent(userId, generated.size()));
            return generated;
        }
        return existing;
    }

    private List<HistoricalOrder> generateMockOrders(Long userId) {
        List<HistoricalOrder> orders = new ArrayList<>();
        Random random = new Random();
        String[] products = {"智能手机 X1", "无线耳机 Pro", "智能手表 Watch 5", "笔记本电脑 Air", "平板电脑 Pad Mini"};
        String[] models = {"128GB 黑色", "白色", "运动版", "M2芯片 16G", "64GB WiFi版"};
        BigDecimal[] prices = {new BigDecimal("2999.00"), new BigDecimal("899.00"), new BigDecimal("1599.00"),
                new BigDecimal("8999.00"), new BigDecimal("2499.00")};

        int count = random.nextInt(3) + 3;
        for (int i = 0; i < count; i++) {
            int idx = random.nextInt(products.length);
            HistoricalOrder order = new HistoricalOrder();
            order.setUserId(userId);
            order.setOrderNo("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            order.setProductName(products[idx]);
            order.setProductModel(models[idx]);
            order.setQuantity(1);
            order.setPrice(prices[idx]);
            order.setTotalAmount(prices[idx]);
            order.setOrderStatus("已完成");
            orders.add(orderRepository.save(order));
        }
        return orders;
    }
}
