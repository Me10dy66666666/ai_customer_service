package com.example.backend.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserProfile {
    private Long id;

    private Long userId;

    private String sessionId;

    private String userType;

    private Double satisfactionScore;

    private String preferredProducts;

    private Integer purchaseFrequency;

    private BigDecimal totalSpending;

    private Integer serviceTimes;

    private LocalDateTime lastPurchaseTime;

    private LocalDateTime lastServiceTime;

    private String tags;

    private LocalDateTime updateTime;
}
