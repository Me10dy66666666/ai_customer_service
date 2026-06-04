package com.example.backend.interfaces.controller;

import com.example.backend.application.service.UserProfileApplicationService;
import com.example.backend.common.Result;
import com.example.backend.domain.profile.model.UserProfile;
import com.example.backend.interfaces.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@RequireRole({"ADMIN"})
public class UserProfileController {
    private final UserProfileApplicationService userProfileApplicationService;

    @GetMapping("/profiles")
    public Result<List<UserProfile>> searchProfiles(
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(userProfileApplicationService.searchProfiles(userType, userId, startTime, endTime));
    }

    @GetMapping("/profile/{userId}")
    public Result<UserProfile> getProfile(@PathVariable Long userId) {
        return Result.success(userProfileApplicationService.getUserProfile(userId));
    }

    @PostMapping("/profile/build/{userId}")
    public Result<UserProfile> buildProfile(@PathVariable Long userId) {
        return Result.success(userProfileApplicationService.buildUserProfile(userId));
    }
}
