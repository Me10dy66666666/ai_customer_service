package com.example.backend.mapper;

import com.example.backend.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface DocumentMapper {
    int insert(Document document);
    int update(Document document);
    int deleteById(Long id);
    Document selectById(Long id);
    List<Document> selectAll();
    
    List<Document> findAllByOrderByCreateTimeDesc();
    boolean existsByTitle(String title);
    Document findByDifyDocumentId(String difyDocumentId);
    
    long countAll();
    long countByStatus(@Param("status") Integer status);
    List<Map<String, Object>> countByCategory();
}
