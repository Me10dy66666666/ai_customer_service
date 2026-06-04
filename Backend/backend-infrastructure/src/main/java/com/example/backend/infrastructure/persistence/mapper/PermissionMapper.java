package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PermissionMapper {
    List<Permission> selectAll();
    List<Permission> selectByRoleId(@Param("roleId") Long roleId);
    int deleteRolePermissions(@Param("roleId") Long roleId);
    int insertRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
