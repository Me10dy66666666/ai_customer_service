package com.example.backend.interfaces.security;

import com.example.backend.common.Result;
import com.example.backend.common.service.RedisService;
import com.example.backend.infrastructure.security.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private static final String PERM_CACHE_PREFIX = "perm:";

    private final JwtUtils jwtUtils;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    public PermissionInterceptor(JwtUtils jwtUtils, RedisService redisService, ObjectMapper objectMapper) {
        this.jwtUtils = jwtUtils;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
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

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(response, 401, "Missing or invalid Authorization header");
            return false;
        }

        String token = authHeader.substring(7);

        Set<String> tokenRoles;
        try {
            tokenRoles = jwtUtils.getRolesFromToken(token);
        } catch (Exception e) {
            writeError(response, 401, "Invalid token");
            return false;
        }

        if (tokenRoles.isEmpty()) {
            writeError(response, 403, "Access denied: missing role information");
            return false;
        }

        Set<String> allPermissions = new HashSet<>();
        for (String role : tokenRoles) {
            Object cached = redisService.get(PERM_CACHE_PREFIX + role);
            if (cached instanceof Set) {
                @SuppressWarnings("unchecked")
                Set<String> permSet = (Set<String>) cached;
                allPermissions.addAll(permSet);
            }
        }

        if (!allPermissions.contains(annotation.value())) {
            log.warn("Permission denied: required={}, roles={}, permissions={}",
                    annotation.value(), tokenRoles, allPermissions);
            writeError(response, 403, "Access denied: insufficient permission");
            return false;
        }

        return true;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.error(status, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
