package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.SlaCalendarSpecialDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface SlaCalendarSpecialDateMapper {
    List<SlaCalendarSpecialDate> findByCalendarId(@Param("calendarId") Long calendarId);
    int insert(SlaCalendarSpecialDate specialDate);
    int deleteById(@Param("id") Long id);
    SlaCalendarSpecialDate findByCalendarIdAndDate(@Param("calendarId") Long calendarId, @Param("specialDate") LocalDate specialDate);
}
