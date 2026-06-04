package com.example.backend.domain.auth.repository;

import com.example.backend.domain.auth.model.Permission;

import java.util.List;

public interface PermissionRepository {
    List<Permission> findAll();
    List<Permission> findByRoleId(Long roleId);
    void saveRolePermissions(Long roleId, List<Long> permissionIds);
}
