package com.example.backend.infrastructure.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OcrSegmentEntity {
    private Long id;
    private Long documentId;
    private Integer segmentIndex;
    private String ocrText;
    private String reviewedText;
    private Double confidence;
    private String boundingBox;
    private String status;
    private LocalDateTime createdAt;
}
