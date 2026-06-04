package com.example.backend.application.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentDto {
    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String email;
    private Integer status;
    private String roleName;
    private List<String> roleNames;
    private List<String> skills;
    private String tags;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
