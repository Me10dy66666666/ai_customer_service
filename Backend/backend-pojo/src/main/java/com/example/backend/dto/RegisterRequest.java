package com.example.backend.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class RegisterRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    private String phone;
    private String sessionId; // Optional visitor session ID for merging data
}
