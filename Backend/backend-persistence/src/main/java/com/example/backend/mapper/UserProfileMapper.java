package com.example.backend.mapper;

import com.example.backend.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserProfileMapper {
    int insert(UserProfile userProfile);
    int update(UserProfile userProfile);
    int deleteById(Long id);
    UserProfile selectById(Long id);
    List<UserProfile> selectAll();
    UserProfile findByUserId(Long userId);
    UserProfile findBySessionId(String sessionId);
    List<UserProfile> listByConditions(@Param("userType") String userType,
                                       @Param("userId") Long userId,
                                       @Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);
}
