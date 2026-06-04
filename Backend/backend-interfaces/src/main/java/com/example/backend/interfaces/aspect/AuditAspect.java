package com.example.backend.interfaces.aspect;

import com.example.backend.infrastructure.persistence.entity.WorkOrderAuditLogEntity;
import com.example.backend.infrastructure.persistence.mapper.UserMapper;
import com.example.backend.infrastructure.persistence.mapper.WorkOrderAuditLogMapper;
import com.example.backend.infrastructure.security.JwtUtils;
import com.example.backend.interfaces.security.Auditable;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final WorkOrderAuditLogMapper auditLogMapper;
    private final HttpServletRequest request;
    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String operation = auditable.operation();

            Long actorId = extractActorId();
            String actorType = actorId != null ? "AGENT" : "SYSTEM";

            WorkOrderAuditLogEntity entity = new WorkOrderAuditLogEntity();
            entity.setWorkOrderId(0L);
            entity.setEventType("AUDIT");
            entity.setActorType(actorType);
            entity.setActorId(actorId);
            entity.setAction(operation);
            entity.setDetail(buildDetail(joinPoint, signature));
            entity.setInternalOnly(true);
            entity.setCreateTime(LocalDateTime.now());

            auditLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("Failed to write audit log for operation '{}'", auditable.operation(), e);
        }
        return result;
    }

    private Long extractActorId() {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                String username = jwtUtils.getUsernameFromToken(token);
                if (username != null) {
                    var user = userMapper.findByUsername(username);
                    return user != null ? user.getId() : null;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract actor from request", e);
        }
        return null;
    }

    private String buildDetail(ProceedingJoinPoint joinPoint, MethodSignature signature) {
        try {
            String[] paramNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            if (paramNames != null && args != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(paramNames.length, args.length); i++) {
                    if (args[i] != null && !(args[i] instanceof HttpServletRequest)
                            && !(args[i] instanceof jakarta.servlet.http.HttpServletResponse)) {
                        if (!sb.isEmpty()) sb.append(", ");
                        sb.append(paramNames[i]).append("=").append(args[i]);
                    }
                }
                String detail = sb.toString();
                return detail.length() > 512 ? detail.substring(0, 512) : detail;
            }
        } catch (Exception e) {
            log.debug("Failed to build audit detail", e);
        }
        return null;
    }
}
