package com.example.backend.interfaces.security;

import com.example.backend.common.Result;
import com.example.backend.infrastructure.security.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Component
public class RoleInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;

    public RoleInterceptor(JwtUtils jwtUtils, ObjectMapper objectMapper) {
        this.jwtUtils = jwtUtils;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                              Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRole annotation = resolveAnnotation(handlerMethod);
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
            tokenRoles = Set.of();
        }

        if (tokenRoles.isEmpty()) {
            writeError(response, 403, "Access denied: missing role information in token");
            return false;
        }

        boolean hasRole = false;
        for (String role : annotation.value()) {
            if (tokenRoles.contains(role)) {
                hasRole = true;
                break;
            }
        }

        if (!hasRole) {
            log.warn("Access denied: tokenRoles={} required={}", tokenRoles, Set.of(annotation.value()));
            writeError(response, 403, "Access denied: insufficient role");
            return false;
        }

        return true;
    }

    private RequireRole resolveAnnotation(HandlerMethod handlerMethod) {
        RequireRole methodAnno = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (methodAnno != null) return methodAnno;
        return handlerMethod.getBeanType().getAnnotation(RequireRole.class);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.error(status, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
