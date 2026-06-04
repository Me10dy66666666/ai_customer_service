package com.example.backend.domain.auth.service;

import java.util.Set;

public interface JwtTokenService {
    String generateToken(String username, Set<String> roles);
    String extractUsername(String token);
    boolean validateToken(String token);
}
