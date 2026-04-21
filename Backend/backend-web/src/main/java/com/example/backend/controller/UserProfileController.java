package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.entity.UserProfile;
import com.example.backend.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/api/analysis")
@CrossOrigin(origins = "*")
public class UserProfileController {

    @Autowired
    private UserProfileService userProfileService;

    @GetMapping("/profiles")
    public Result<List<UserProfile>> searchProfiles(
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime
    ) {
        List<UserProfile> profiles = userProfileService.searchProfiles(userType, userId, startTime, endTime);
        
        // Sort in memory: Member > Registered > High-Potential > Unregistered
        profiles.sort((p1, p2) -> {
            int score1 = getUserTypeScore(p1.getUserType());
            int score2 = getUserTypeScore(p2.getUserType());
            return Integer.compare(score2, score1); // Descending
        });
        
        return Result.success(profiles);
    }
    
    private int getUserTypeScore(String type) {
        if (type == null) return 0;
        switch (type.toUpperCase()) {
            case "MEMBER": return 4;
            case "REGISTERED": return 3;
            case "HIGH_POTENTIAL": return 2;
            case "UNREGISTERED": return 1;
            default: return 0;
        }
    }

    /**
     * Get user profile analysis.
     * Task M-04: User profile analysis
     */
    @GetMapping("/profile/{userId}")
    public Result<UserProfile> getUserProfile(@PathVariable Long userId) {
        UserProfile profile = userProfileService.getUserProfile(userId);
        return Result.success(profile);
    }

    /**
     * Trigger user profile build/update.
     * Task L-02: User profile automatic construction
     */
    @PostMapping("/profile/build/{userId}")
    public Result<UserProfile> buildUserProfile(@PathVariable Long userId) {
        try {
            UserProfile profile = userProfileService.buildUserProfile(userId);
            return Result.success(profile);
        } catch (Exception e) {
            return Result.error(500, "Failed to build user profile: " + e.getMessage());
        }
    }
}
