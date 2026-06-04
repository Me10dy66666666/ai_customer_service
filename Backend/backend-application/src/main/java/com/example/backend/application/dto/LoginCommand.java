package com.example.backend.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginCommand {
    private String username;
    private String password;
    private String sessionId;

    public boolean hasSessionId() {
        return sessionId != null && !sessionId.isEmpty();
    }
}
