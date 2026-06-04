package com.example.backend.domain.profile.repository;

import com.example.backend.domain.profile.model.UserProfile;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserProfileRepository {
    UserProfile save(UserProfile profile);
    void deleteById(Long id);
    Optional<UserProfile> findById(Long id);
    Optional<UserProfile> findByUserId(Long userId);
    Optional<UserProfile> findBySessionId(String sessionId);
    List<UserProfile> listByConditions(String userType, Long userId,
                                       LocalDateTime startDate, LocalDateTime endDate);
}
