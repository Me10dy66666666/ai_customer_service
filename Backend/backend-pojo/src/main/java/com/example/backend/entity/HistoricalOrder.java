package com.example.backend.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class HistoricalOrder {
    private Long id;

    private Long userId;

    private String orderNo;

    private String productName;

    private String productModel;

    private Integer quantity;

    private BigDecimal price;

    private BigDecimal totalAmount;

    private String orderStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
