package com.example.backend.infrastructure.persistence.entity;

import lombok.Data;

@Data
public class Permission {
    private Long id;
    private String code;
    private String name;
    private String resource;
    private String action;
    private String description;
}
