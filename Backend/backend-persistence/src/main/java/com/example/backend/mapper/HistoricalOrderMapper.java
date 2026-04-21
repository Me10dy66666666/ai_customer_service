package com.example.backend.mapper;

import com.example.backend.entity.HistoricalOrder;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface HistoricalOrderMapper {
    int insert(HistoricalOrder order);
    int update(HistoricalOrder order);
    int deleteById(Long id);
    HistoricalOrder selectById(Long id);
    List<HistoricalOrder> selectAll();
    
    List<HistoricalOrder> findByUserId(Long userId);
    List<HistoricalOrder> findByUserIdOrderByCreateTimeDesc(Long userId);
}
