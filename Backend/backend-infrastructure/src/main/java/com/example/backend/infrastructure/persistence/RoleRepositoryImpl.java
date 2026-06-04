package com.example.backend.infrastructure.persistence;

import com.example.backend.domain.auth.repository.RoleRepository;
import com.example.backend.domain.auth.model.Role;
import com.example.backend.infrastructure.persistence.mapper.RoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {
    private final RoleMapper roleMapper;

    @Override
    public Optional<Role> findById(Long id) {
        return Optional.ofNullable(roleMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Role> findByRoleName(String roleName) {
        return Optional.ofNullable(roleMapper.findByRoleName(roleName)).map(this::toDomain);
    }

    @Override
    public List<Role> findAll() {
        return roleMapper.selectAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Role save(Role role) {
        if (role.getId() == null) {
            com.example.backend.infrastructure.persistence.entity.Role po = toEntity(role);
            roleMapper.insert(po);
            role.setId(po.getId());
            return role;
        } else {
            roleMapper.update(toEntity(role));
            return role;
        }
    }

    @Override
    public void deleteById(Long id) {
        roleMapper.deleteById(id);
    }

    @Override
    public void clearRolePermissions(Long roleId) {
        roleMapper.deleteRolePermissions(roleId);
    }

    @Override
    public void insertRolePermission(Long roleId, Long permissionId) {
        roleMapper.insertRolePermission(roleId, permissionId);
    }

    private Role toDomain(com.example.backend.infrastructure.persistence.entity.Role po) {
        Role role = new Role();
        role.setId(po.getId()); role.setRoleName(po.getRoleName()); role.setDescription(po.getDescription());
        return role;
    }

    private com.example.backend.infrastructure.persistence.entity.Role toEntity(Role role) {
        com.example.backend.infrastructure.persistence.entity.Role po = new com.example.backend.infrastructure.persistence.entity.Role();
        po.setId(role.getId()); po.setRoleName(role.getRoleName()); po.setDescription(role.getDescription());
        return po;
    }
}
