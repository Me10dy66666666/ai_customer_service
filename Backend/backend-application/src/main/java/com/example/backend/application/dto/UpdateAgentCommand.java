package com.example.backend.application.dto;

import lombok.Data;
import java.util.List;

@Data
public class UpdateAgentCommand {
    private String nickname;
    private String phone;
    private String email;
    private String password;
    private Integer status;
    private String roleName;
    private List<String> skills;
}
