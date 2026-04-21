package com.example.backend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WorkOrder {
    private Long id;

    private Long userId;

    private String title;

    private String description;

    private String type;

    private String priority = "medium";

    private String status = "pending";

    private Long handlerId;

    private String result;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
