package com.example.backend.domain.auth.service;

public interface PasswordEncodingService {
    String encode(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}
