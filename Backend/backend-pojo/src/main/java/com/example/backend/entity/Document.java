package com.example.backend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Document {
    private Long id;

    private String title;

    private String content;

    private String category;

    private String fileType;

    private String filePath;

    private String difyDocumentId;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
