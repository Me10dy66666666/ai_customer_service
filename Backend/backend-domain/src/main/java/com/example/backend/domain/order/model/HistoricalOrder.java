package com.example.backend.domain.order.model;

import com.example.backend.domain.shared.model.BaseAggregateRoot;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class HistoricalOrder extends BaseAggregateRoot {
    private Long id;
    private Long userId;
    private String orderNo;
    private String productName;
    private String productModel;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal totalAmount;
    private String orderStatus;

    public BigDecimal safeTotalAmount() {
        return totalAmount != null ? totalAmount : BigDecimal.ZERO;
    }
}
