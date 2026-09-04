package com.example.backend.interfaces.security;

import com.example.backend.common.Result;
import com.example.backend.common.service.RedisService;
import com.example.backend.domain.auth.model.Permission;
import com.example.backend.domain.auth.repository.PermissionRepository;
import com.example.backend.domain.auth.repository.RoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private static final String PERM_CACHE_PREFIX = "perm:";

    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public PermissionInterceptor(RedisService redisService, ObjectMapper objectMapper,
                                 RoleRepository roleRepository,
                                 PermissionRepository permissionRepository) {
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                              Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequirePermission annotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (annotation == null) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            writeError(response, 401, "Authentication is required");
            return false;
        }

        Set<String> currentRoles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .collect(java.util.stream.Collectors.toSet());

        if (currentRoles.isEmpty()) {
            writeError(response, 403, "Access denied: missing role information");
            return false;
        }

        Set<String> allPermissions = new HashSet<>();
        for (String role : currentRoles) {
            allPermissions.addAll(resolvePermissions(role));
        }

        if (!allPermissions.contains(annotation.value())) {
            log.warn("Permission denied: required={}, roles={}, permissions={}",
                    annotation.value(), currentRoles, allPermissions);
            writeError(response, 403, "Access denied: insufficient permission");
            return false;
        }

        return true;
    }

    private Set<String> resolvePermissions(String roleName) {
        Object cached = redisService.get(PERM_CACHE_PREFIX + roleName);
        if (cached instanceof Collection<?> values) {
            Set<String> permissions = new HashSet<>();
            values.stream().filter(String.class::isInstance).map(String.class::cast)
                    .forEach(permissions::add);
            return permissions;
        }

        Set<String> permissions = roleRepository.findByRoleName(roleName)
                .map(role -> permissionRepository.findByRoleId(role.getId()).stream()
                        .map(Permission::getCode)
                        .collect(java.util.stream.Collectors.toSet()))
                .orElseGet(Set::of);
        redisService.set(PERM_CACHE_PREFIX + roleName, permissions);
        return permissions;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.error(status, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
