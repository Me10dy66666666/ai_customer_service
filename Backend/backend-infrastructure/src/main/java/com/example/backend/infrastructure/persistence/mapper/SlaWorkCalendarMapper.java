package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.SlaWorkCalendar;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SlaWorkCalendarMapper {
    List<SlaWorkCalendar> findAllActive();
    SlaWorkCalendar selectById(@Param("id") Long id);
    int insert(SlaWorkCalendar calendar);
    int update(SlaWorkCalendar calendar);
}
