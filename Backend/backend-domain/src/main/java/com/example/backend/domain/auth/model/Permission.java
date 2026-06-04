package com.example.backend.domain.auth.model;

import com.example.backend.domain.shared.model.BaseAggregateRoot;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Permission extends BaseAggregateRoot {
    private Long id;
    private String code;
    private String name;
    private String resource;
    private String action;
    private String description;
}
