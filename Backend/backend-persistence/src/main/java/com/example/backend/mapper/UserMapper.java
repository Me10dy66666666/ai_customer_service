package com.example.backend.mapper;

import com.example.backend.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserMapper {
    int insert(User user);
    int update(User user);
    int deleteById(Long id);
    User selectById(Long id);
    List<User> selectAll();
    
    User findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByPhone(String phone);
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
