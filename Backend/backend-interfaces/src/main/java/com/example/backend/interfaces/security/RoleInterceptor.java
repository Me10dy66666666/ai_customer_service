package com.example.backend.interfaces.security;

import com.example.backend.common.Result;
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
import java.util.Set;

@Slf4j
@Component
public class RoleInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public RoleInterceptor(ObjectMapper objectMapper) {
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

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            writeError(response, 401, "Authentication is required");
            return false;
        }
        Set<String> tokenRoles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .collect(java.util.stream.Collectors.toSet());

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
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.error(status, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
