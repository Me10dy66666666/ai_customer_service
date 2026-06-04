package com.example.backend.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

@Data
@AllArgsConstructor
public class LoginResult {
    private String token;
    private Long userId;
    private Set<String> roles;
}
