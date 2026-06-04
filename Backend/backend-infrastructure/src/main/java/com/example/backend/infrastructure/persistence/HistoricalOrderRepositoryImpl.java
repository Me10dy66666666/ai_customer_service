package com.example.backend.infrastructure.persistence;

import com.example.backend.domain.order.model.HistoricalOrder;
import com.example.backend.domain.order.repository.HistoricalOrderRepository;
import com.example.backend.infrastructure.persistence.mapper.HistoricalOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class HistoricalOrderRepositoryImpl implements HistoricalOrderRepository {
    private final HistoricalOrderMapper mapper;

    @Override
    public HistoricalOrder save(HistoricalOrder order) {
        com.example.backend.infrastructure.persistence.entity.HistoricalOrder po = toEntity(order);
        if (po.getId() == null) mapper.insert(po); else mapper.update(po);
        return toDomain(mapper.selectById(po.getId()));
    }

    @Override public List<HistoricalOrder> findByUserId(Long userId) {
        return mapper.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override public List<HistoricalOrder> findByUserIdOrderByCreateTimeDesc(Long userId) {
        return mapper.findByUserIdOrderByCreateTimeDesc(userId).stream().map(this::toDomain).toList();
    }

    private HistoricalOrder toDomain(com.example.backend.infrastructure.persistence.entity.HistoricalOrder po) {
        HistoricalOrder o = new HistoricalOrder();
        o.setId(po.getId()); o.setUserId(po.getUserId()); o.setOrderNo(po.getOrderNo());
        o.setProductName(po.getProductName()); o.setProductModel(po.getProductModel());
        o.setQuantity(po.getQuantity()); o.setPrice(po.getPrice()); o.setTotalAmount(po.getTotalAmount());
        o.setOrderStatus(po.getOrderStatus()); o.setCreateTime(po.getCreateTime()); o.setUpdateTime(po.getUpdateTime());
        return o;
    }

    private com.example.backend.infrastructure.persistence.entity.HistoricalOrder toEntity(HistoricalOrder o) {
        com.example.backend.infrastructure.persistence.entity.HistoricalOrder po = new com.example.backend.infrastructure.persistence.entity.HistoricalOrder();
        po.setId(o.getId()); po.setUserId(o.getUserId()); po.setOrderNo(o.getOrderNo());
        po.setProductName(o.getProductName()); po.setProductModel(o.getProductModel());
        po.setQuantity(o.getQuantity()); po.setPrice(o.getPrice()); po.setTotalAmount(o.getTotalAmount());
        po.setOrderStatus(o.getOrderStatus());
        return po;
    }
}
