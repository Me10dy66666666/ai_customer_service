package com.example.backend.infrastructure.persistence;

import com.example.backend.domain.auth.repository.UserRepository;
import com.example.backend.domain.auth.model.Role;
import com.example.backend.infrastructure.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    private final UserMapper userMapper;

    @Override
    public com.example.backend.domain.auth.model.User save(com.example.backend.domain.auth.model.User user) {
        if (user.getId() == null) {
            com.example.backend.infrastructure.persistence.entity.User po = toEntity(user);
            userMapper.insert(po);
            user.setId(po.getId());
            return toDomain(po);
        } else {
            userMapper.update(toEntity(user));
            return user;
        }
    }

    @Override
    public void saveUserRole(Long userId, Long roleId) { userMapper.insertUserRole(userId, roleId); }

    @Override
    public void clearUserRoles(Long userId) { userMapper.deleteUserRole(userId); }

    @Override
    public Optional<com.example.backend.domain.auth.model.User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<com.example.backend.domain.auth.model.User> findByUsername(String username) {
        return Optional.ofNullable(userMapper.findByUsername(username)).map(this::toDomain);
    }

    @Override
    public java.util.List<com.example.backend.domain.auth.model.User> findByUserType(Integer userType) {
        return userMapper.findByUserType(userType).stream().map(this::toDomain).toList();
    }

    @Override
    public java.util.List<com.example.backend.domain.auth.model.User> findByRoleName(String roleName) {
        return userMapper.findByRoleName(roleName).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByUsername(String username) { return userMapper.existsByUsername(username); }

    @Override
    public boolean existsByUsernameExcludingId(String username, Long excludeId) {
        return userMapper.existsByUsernameExcludingId(username, excludeId);
    }

    @Override
    public boolean existsByPhone(String phone) { return userMapper.existsByPhone(phone); }

    @Override
    public boolean existsByPhoneExcludingId(String phone, Long excludeId) {
        return userMapper.existsByPhoneExcludingId(phone, excludeId);
    }

    @Override
    public void deleteById(Long id) { userMapper.deleteById(id); }

    @Override
    public void deleteUserRole(Long userId) { userMapper.deleteUserRole(userId); }

    @Override
    public java.util.List<com.example.backend.domain.auth.model.User> findAll() {
        return userMapper.selectAll().stream().map(this::toDomain).toList();
    }

    @Override
    public void batchUpdateStatus(java.util.List<Long> ids, Integer status) {
        if (ids != null && !ids.isEmpty()) {
            userMapper.batchUpdateStatus(ids, status);
        }
    }

    private com.example.backend.domain.auth.model.User toDomain(
            com.example.backend.infrastructure.persistence.entity.User po) {
        com.example.backend.domain.auth.model.User user = new com.example.backend.domain.auth.model.User();
        user.setId(po.getId()); user.setUsername(po.getUsername()); user.setPassword(po.getPassword());
        user.setNickname(po.getNickname()); user.setPhone(po.getPhone()); user.setEmail(po.getEmail());
        user.setStatus(po.getStatus());
        user.setCreateTime(po.getCreateTime()); user.setUpdateTime(po.getUpdateTime());
        if (po.getRoles() != null) for (com.example.backend.infrastructure.persistence.entity.Role r : po.getRoles()) {
            Role role = new Role(); role.setId(r.getId()); role.setRoleName(r.getRoleName());
            role.setDescription(r.getDescription()); user.getRoles().add(role);
        }
        return user;
    }

    private com.example.backend.infrastructure.persistence.entity.User toEntity(
            com.example.backend.domain.auth.model.User user) {
        com.example.backend.infrastructure.persistence.entity.User po =
                new com.example.backend.infrastructure.persistence.entity.User();
        po.setId(user.getId()); po.setUsername(user.getUsername()); po.setPassword(user.getPassword());
        po.setNickname(user.getNickname()); po.setPhone(user.getPhone()); po.setEmail(user.getEmail());
        po.setStatus(user.getStatus());
        return po;
    }
}
