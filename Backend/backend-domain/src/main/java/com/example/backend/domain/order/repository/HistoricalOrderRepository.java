package com.example.backend.domain.order.repository;

import com.example.backend.domain.order.model.HistoricalOrder;
import java.util.List;

public interface HistoricalOrderRepository {
    HistoricalOrder save(HistoricalOrder order);
    List<HistoricalOrder> findByUserId(Long userId);
    List<HistoricalOrder> findByUserIdOrderByCreateTimeDesc(Long userId);
}
