package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RoleMapper {
    int insert(Role role);
    int update(Role role);
    int deleteById(Long id);
    Role selectById(Long id);
    List<Role> selectAll();
    Role findByRoleName(String roleName);
    int deleteRolePermissions(@Param("roleId") Long roleId);
    int insertRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
