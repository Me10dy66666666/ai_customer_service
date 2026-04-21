package com.example.backend.service;

import com.example.backend.common.BusinessException;
import com.example.backend.common.exception.ForbiddenException;
import com.example.backend.common.exception.UnauthorizedException;
import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.dto.RegisterRequest;
import com.example.backend.dto.RegisterResponse;
import com.example.backend.entity.User;
import com.example.backend.entity.Role;
import com.example.backend.mapper.RoleMapper;
import com.example.backend.mapper.UserMapper;
import com.example.backend.security.JwtUtils;
import com.example.backend.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final UserProfileService userProfileService;

    public RegisterResponse register(RegisterRequest request) {
        if (userMapper.existsByUsername(request.getUsername())) {
            throw new BusinessException(400, "Username already exists");
        }
        if (request.getPhone() != null && userMapper.existsByPhone(request.getPhone())) {
            throw new BusinessException(400, "Phone already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setUserType(1); // Default normal user (1)
        user.setStatus(1); // Default active

        // Assign default role (USER / ID 2)
        Role userRole = roleMapper.selectById(2L);
        if (userRole != null) {
            user.getRoles().add(userRole);
        }

        userMapper.insert(user);
        if (userRole != null) {
            userMapper.insertUserRole(user.getId(), userRole.getId());
        }

        // Merge visitor data if sessionId is provided
        if (request.getSessionId() != null && !request.getSessionId().isEmpty()) {
            userProfileService.mergeVisitorToUser(request.getSessionId(), user.getId());
        }

        return new RegisterResponse(user.getId());
    }

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.findByUsername(request.getUsername());

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        if (user.getStatus() == 0) {
            throw new ForbiddenException("Account is disabled");
        }

        // Merge visitor data if sessionId is provided
        if (request.getSessionId() != null && !request.getSessionId().isEmpty()) {
            userProfileService.mergeVisitorToUser(request.getSessionId(), user.getId());
        }

        String token = jwtUtils.generateToken(user.getUsername(), user.getUserType());
        return new LoginResponse(token, user.getUserType(), user.getId());
    }
}
