package com.example.backend.domain.auth.repository;

import com.example.backend.domain.auth.model.Role;
import java.util.List;
import java.util.Optional;

public interface RoleRepository {
    Optional<Role> findById(Long id);
    Optional<Role> findByRoleName(String roleName);
    List<Role> findAll();
    Role save(Role role);
    void deleteById(Long id);
    void clearRolePermissions(Long roleId);
    void insertRolePermission(Long roleId, Long permissionId);
}
