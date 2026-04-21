package com.example.backend.entity;

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

    private Integer userType; // 1-普通用户, 2-会员, 3-管理员

    private Integer status; // 0-禁用, 1-启用

    private java.util.Set<Role> roles = new java.util.HashSet<>();

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
