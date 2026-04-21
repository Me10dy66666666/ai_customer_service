package com.example.backend.mapper;

import com.example.backend.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface RoleMapper {
    int insert(Role role);
    int update(Role role);
    int deleteById(Long id);
    Role selectById(Long id);
    List<Role> selectAll();
    Role findByRoleName(String roleName);
}
