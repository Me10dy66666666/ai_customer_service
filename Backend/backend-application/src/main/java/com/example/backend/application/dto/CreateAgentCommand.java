package com.example.backend.application.dto;

import lombok.Data;
import java.util.List;

@Data
public class CreateAgentCommand {
    private String username;
    private String password;
    private String nickname;
    private String phone;
    private String email;
    private String roleName;
    private List<String> skills;
}
