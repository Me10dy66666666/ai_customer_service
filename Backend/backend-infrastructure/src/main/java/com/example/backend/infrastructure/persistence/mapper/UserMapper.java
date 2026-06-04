package com.example.backend.infrastructure.persistence.mapper;

import com.example.backend.infrastructure.persistence.entity.User;
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
    List<User> findByUserType(@Param("userType") Integer userType);
    List<User> findByRoleName(@Param("roleName") String roleName);
    boolean existsByUsername(String username);
    boolean existsByUsernameExcludingId(@Param("username") String username, @Param("excludeId") Long excludeId);
    boolean existsByPhone(String phone);
    boolean existsByPhoneExcludingId(@Param("phone") String phone, @Param("excludeId") Long excludeId);
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
    int deleteUserRole(@Param("userId") Long userId);
    int batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") Integer status);
}
