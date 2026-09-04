package com.example.backend.interfaces.security;

import com.example.backend.common.service.RedisService;
import com.example.backend.domain.auth.model.Permission;
import com.example.backend.domain.auth.model.Role;
import com.example.backend.domain.auth.repository.PermissionRepository;
import com.example.backend.domain.auth.repository.RoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionInterceptorTest {

    private final RedisService redisService = mock(RedisService.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PermissionRepository permissionRepository = mock(PermissionRepository.class);
    private final PermissionInterceptor interceptor = new PermissionInterceptor(
            redisService, new ObjectMapper(), roleRepository, permissionRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedRequestReturnsReal401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), response, handler());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void currentDatabaseRolePermissionAllowsRequestOnCacheMiss() throws Exception {
        authenticate("ROLE_AGENT");
        Role role = new Role();
        role.setId(2L);
        role.setRoleName("AGENT");
        Permission permission = new Permission();
        permission.setCode("work_order:manage");
        when(roleRepository.findByRoleName("AGENT")).thenReturn(Optional.of(role));
        when(permissionRepository.findByRoleId(2L)).thenReturn(List.of(permission));

        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest(), new MockHttpServletResponse(), handler());

        assertThat(allowed).isTrue();
        verify(redisService).set("perm:AGENT", java.util.Set.of("work_order:manage"));
    }

    @Test
    void insufficientCurrentRolePermissionReturnsReal403() throws Exception {
        authenticate("ROLE_USER");
        when(roleRepository.findByRoleName("USER")).thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), response, handler());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "current-user", null, List.of(new SimpleGrantedAuthority(authority))));
    }

    private HandlerMethod handler() throws NoSuchMethodException {
        Method method = SecuredHandler.class.getDeclaredMethod("manage");
        return new HandlerMethod(new SecuredHandler(), method);
    }

    private static final class SecuredHandler {
        @RequirePermission("work_order:manage")
        public void manage() {
            // Marker method used by HandlerMethod in this unit test.
        }
    }
}
