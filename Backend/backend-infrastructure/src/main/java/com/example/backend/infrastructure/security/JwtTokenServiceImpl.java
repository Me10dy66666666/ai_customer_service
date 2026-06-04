package com.example.backend.infrastructure.security;

import com.example.backend.domain.auth.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtTokenServiceImpl implements JwtTokenService {
    private final JwtUtils jwtUtils;

    @Override
    public String generateToken(String username, Set<String> roles) {
        return jwtUtils.generateToken(username, roles);
    }

    @Override
    public String extractUsername(String token) {
        return jwtUtils.getUsernameFromToken(token);
    }

    @Override
    public boolean validateToken(String token) {
        return jwtUtils.validateToken(token);
    }
}
