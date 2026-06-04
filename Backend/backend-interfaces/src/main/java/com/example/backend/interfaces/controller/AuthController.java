package com.example.backend.interfaces.controller;

import com.example.backend.application.dto.LoginCommand;
import com.example.backend.application.dto.RegisterCommand;
import com.example.backend.application.service.AuthApplicationService;
import com.example.backend.common.Result;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthApplicationService authApplicationService;

    @PostMapping("/register")
    public Result<Object> register(@Valid @RequestBody RegisterRequest request) {
        RegisterCommand command = new RegisterCommand(
                request.getUsername(), request.getPassword(),
                request.getPhone(), request.getSessionId());
        return Result.success(authApplicationService.register(command));
    }

    @PostMapping("/login")
    public Result<Object> login(@Valid @RequestBody LoginRequest request) {
        LoginCommand command = new LoginCommand(
                request.getUsername(), request.getPassword(), request.getSessionId());
        return Result.success(authApplicationService.login(command));
    }

    @Data
    static class LoginRequest {
        @jakarta.validation.constraints.NotBlank private String username;
        @jakarta.validation.constraints.NotBlank private String password;
        private String sessionId;
    }

    @Data
    static class RegisterRequest {
        @jakarta.validation.constraints.NotBlank private String username;
        @jakarta.validation.constraints.NotBlank private String password;
        private String phone;
        private String sessionId;
    }
}
