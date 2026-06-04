package com.example.backend.domain.auth.repository;

import com.example.backend.domain.auth.model.User;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    void saveUserRole(Long userId, Long roleId);
    void clearUserRoles(Long userId);
    Optional<User> findById(Long id);
    Optional<User> findByUsername(String username);
    List<User> findByUserType(Integer userType);
    List<User> findByRoleName(String roleName);
    boolean existsByUsername(String username);
    boolean existsByUsernameExcludingId(String username, Long excludeId);
    boolean existsByPhone(String phone);
    boolean existsByPhoneExcludingId(String phone, Long excludeId);
    void deleteById(Long id);
    void deleteUserRole(Long userId);
    List<User> findAll();
    void batchUpdateStatus(List<Long> ids, Integer status);
}
