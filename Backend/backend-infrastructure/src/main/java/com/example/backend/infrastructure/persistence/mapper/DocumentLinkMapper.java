package com.example.backend.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface DocumentLinkMapper {
    List<Map<String, Object>> findBySourceDocId(@Param("sourceDocId") Long sourceDocId);
    List<Map<String, Object>> findByTargetDocId(@Param("targetDocId") Long targetDocId);
}
