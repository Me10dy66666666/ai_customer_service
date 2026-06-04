package com.example.backend.infrastructure.persistence;

import com.example.backend.domain.auth.model.Permission;
import com.example.backend.domain.auth.repository.PermissionRepository;
import com.example.backend.infrastructure.persistence.mapper.PermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PermissionRepositoryImpl implements PermissionRepository {
    private final PermissionMapper permissionMapper;

    @Override
    public List<Permission> findAll() {
        return permissionMapper.selectAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Permission> findByRoleId(Long roleId) {
        return permissionMapper.selectByRoleId(roleId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void saveRolePermissions(Long roleId, List<Long> permissionIds) {
        if (roleId == null) {
            throw new IllegalArgumentException("roleId must not be null");
        }
        permissionMapper.deleteRolePermissions(roleId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permissionId : permissionIds) {
                if (permissionId != null) {
                    permissionMapper.insertRolePermission(roleId, permissionId);
                }
            }
        }
    }

    private Permission toDomain(com.example.backend.infrastructure.persistence.entity.Permission po) {
        Permission perm = new Permission();
        perm.setId(po.getId());
        perm.setCode(po.getCode());
        perm.setName(po.getName());
        perm.setResource(po.getResource());
        perm.setAction(po.getAction());
        perm.setDescription(po.getDescription());
        return perm;
    }
}
