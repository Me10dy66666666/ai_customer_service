package com.example.backend.interfaces.controller;

import com.example.backend.common.Result;
import com.example.backend.common.service.RedisService;
import com.example.backend.domain.auth.model.Permission;
import com.example.backend.domain.auth.model.Role;
import com.example.backend.domain.auth.repository.PermissionRepository;
import com.example.backend.domain.auth.repository.RoleRepository;
import com.example.backend.domain.auth.repository.UserRepository;
import com.example.backend.interfaces.security.RequireRole;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RequireRole({"ADMIN"})
public class RoleManagementController {

    private static final String PERM_CACHE_PREFIX = "perm:";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final RedisService redisService;

    @GetMapping("/roles")
    public Result<List<Role>> listRoles() {
        return Result.success(roleRepository.findAll());
    }

    @PostMapping("/roles")
    @Transactional
    public Result<Role> createRole(@RequestBody RoleRequest request) {
        Role role = new Role();
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        Role saved = roleRepository.save(role);
        return Result.success(saved);
    }

    @PutMapping("/roles/{id}")
    @Transactional
    public Result<Role> updateRole(@PathVariable Long id, @RequestBody RoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found"));
        if (request.getRoleName() != null) {
            role.setRoleName(request.getRoleName());
        }
        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }
        Role saved = roleRepository.save(role);
        return Result.success(saved);
    }

    @DeleteMapping("/roles/{id}")
    @Transactional
    public Result<?> deleteRole(@PathVariable Long id) {
        roleRepository.deleteById(id);
        return Result.success(null);
    }

    @GetMapping("/roles/{id}/permissions")
    public Result<List<Permission>> getRolePermissions(@PathVariable Long id) {
        return Result.success(permissionRepository.findByRoleId(id));
    }

    @PutMapping("/roles/{id}/permissions")
    @Transactional
    public Result<?> setRolePermissions(@PathVariable Long id, @RequestBody PermissionIdsRequest request) {
        roleRepository.clearRolePermissions(id);
        if (request.getPermissionIds() != null) {
            for (Long permId : request.getPermissionIds()) {
                roleRepository.insertRolePermission(id, permId);
            }
        }
        refreshPermissionCache(id);
        return Result.success(null);
    }

    @GetMapping("/permissions")
    public Result<List<Permission>> listPermissions() {
        return Result.success(permissionRepository.findAll());
    }

    @PutMapping("/users/{id}/roles")
    @Transactional
    public Result<?> assignUserRoles(@PathVariable Long id, @RequestBody RoleIdsRequest request) {
        userRepository.clearUserRoles(id);
        if (request.getRoleIds() != null) {
            for (Long roleId : request.getRoleIds()) {
                userRepository.saveUserRole(id, roleId);
            }
        }
        return Result.success(null);
    }

    private void refreshPermissionCache(Long roleId) {
        roleRepository.findById(roleId).ifPresent(role -> {
            List<Permission> perms = permissionRepository.findByRoleId(roleId);
            Set<String> permCodes = perms.stream()
                    .map(Permission::getCode)
                    .collect(Collectors.toSet());
            redisService.set(PERM_CACHE_PREFIX + role.getRoleName(), permCodes);
        });
    }

    @Data
    static class RoleRequest {
        private String roleName;
        private String description;
    }

    @Data
    static class PermissionIdsRequest {
        private List<Long> permissionIds;
    }

    @Data
    static class RoleIdsRequest {
        private List<Long> roleIds;
    }
}
