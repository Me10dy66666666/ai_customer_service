package com.example.backend.application.service;

import com.example.backend.application.dto.LoginCommand;
import com.example.backend.application.dto.LoginResult;
import com.example.backend.application.dto.RegisterCommand;
import com.example.backend.application.dto.RegisterResult;
import com.example.backend.common.BusinessException;
import com.example.backend.common.exception.ForbiddenException;
import com.example.backend.common.exception.UnauthorizedException;
import com.example.backend.domain.auth.event.UserLoggedInEvent;
import com.example.backend.domain.auth.event.UserRegisteredEvent;
import com.example.backend.domain.auth.model.User;
import com.example.backend.domain.auth.model.Role;
import com.example.backend.domain.auth.repository.RoleRepository;
import com.example.backend.domain.auth.repository.UserRepository;
import com.example.backend.domain.auth.service.JwtTokenService;
import com.example.backend.domain.auth.service.PasswordEncodingService;
import com.example.backend.domain.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthApplicationService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncodingService passwordEncodingService;
    private final JwtTokenService jwtTokenService;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public RegisterResult register(RegisterCommand command) {
        if (userRepository.existsByUsername(command.getUsername())) {
            throw new BusinessException(400, "Username already exists");
        }
        if (command.getPhone() != null && userRepository.existsByPhone(command.getPhone())) {
            throw new BusinessException(400, "Phone already exists");
        }

        User user = new User();
        user.setUsername(command.getUsername());
        user.setPassword(passwordEncodingService.encode(command.getPassword()));
        user.setPhone(command.getPhone());
        user.setStatus(1);

        Role userRole = roleRepository.findById(2L).orElse(null);
        if (userRole != null) {
            user.assignRole(userRole);
        }

        User saved = userRepository.save(user);
        if (userRole != null) {
            userRepository.saveUserRole(saved.getId(), userRole.getId());
        }

        eventPublisher.publish(new UserRegisteredEvent(saved.getId(), saved.getUsername(), command.getSessionId()));
        return new RegisterResult(saved.getId());
    }

    @Transactional
    public LoginResult login(LoginCommand command) {
        User user = userRepository.findByUsername(command.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!passwordEncodingService.matches(command.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        if (user.isDisabled()) {
            throw new ForbiddenException("Account is disabled");
        }

        String token = jwtTokenService.generateToken(user.getUsername(), user.roleNames());
        eventPublisher.publish(new UserLoggedInEvent(user.getId(), user.getUsername(),
                user.roleNames(), command.getSessionId()));
        return new LoginResult(token, user.getId(), user.roleNames());
    }
}
