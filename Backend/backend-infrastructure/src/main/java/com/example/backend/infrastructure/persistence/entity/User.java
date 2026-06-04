package com.example.backend.infrastructure.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String phone;
    private String email;
    private Integer userType;
    private Integer status;
    private java.util.Set<Role> roles = new java.util.HashSet<>();
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
