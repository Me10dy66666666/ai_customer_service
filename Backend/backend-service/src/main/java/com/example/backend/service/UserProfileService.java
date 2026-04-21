package com.example.backend.service;

import com.example.backend.entity.HistoricalOrder;
import com.example.backend.entity.UserProfile;
import com.example.backend.entity.ConsultationLog;
import com.example.backend.mapper.HistoricalOrderMapper;
import com.example.backend.mapper.UserProfileMapper;
import com.example.backend.mapper.ConsultationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileMapper userProfileMapper;
    private final ConsultationLogMapper consultationLogMapper;
    private final HistoricalOrderMapper orderMapper;

    /**
     * Get user profile by user ID.
     * If not found, build it automatically.
     */
    @Cacheable(value = "userProfile", key = "#userId")
    public UserProfile getUserProfile(Long userId) {
        UserProfile profile = userProfileMapper.findByUserId(userId);
        return profile != null ? profile : buildUserProfile(userId);
    }

    @Cacheable(value = "userProfile", key = "#sessionId")
    public UserProfile getOrCreateVisitorProfile(String sessionId) {
        UserProfile existing = userProfileMapper.findBySessionId(sessionId);
        if (existing != null) {
            return existing;
        }
        UserProfile profile = new UserProfile();
        profile.setSessionId(sessionId);
        profile.setUserType("UNREGISTERED");
        profile.setServiceTimes(0);
        profile.setSatisfactionScore(0.0);
        profile.setTags("Visitor");
        saveProfile(profile);
        return profile;
    }

    @CacheEvict(value = "userProfile", key = "#sessionId")
    public void updateVisitorStats(String sessionId) {
        UserProfile profile = getOrCreateVisitorProfile(sessionId);
        List<ConsultationLog> logs = consultationLogMapper.findBySessionIdOrderByCreateTimeAsc(sessionId);
        
        if (logs.isEmpty()) return;

        profile.setServiceTimes(logs.size());
        profile.setLastServiceTime(logs.get(logs.size() - 1).getCreateTime());

        // Calculate Average Satisfaction (CSAT)
        double avgSat = logs.stream()
                .filter(l -> l.getSatisfaction() != null)
                .mapToInt(ConsultationLog::getSatisfaction)
                .average()
                .orElse(0.0);
        profile.setSatisfactionScore(avgSat);

        // Update User Type based on frequency (High-Potential logic)
        if ("UNREGISTERED".equals(profile.getUserType()) && profile.getServiceTimes() > 5) { // Threshold > Avg Frequency (mocked as 5)
            profile.setUserType("HIGH_POTENTIAL");
        }
        
        saveProfile(profile);
    }

    @Transactional
    @CacheEvict(value = "userProfile", allEntries = true)
    public void mergeVisitorToUser(String sessionId, Long userId) {
        UserProfile visitorProfile = userProfileMapper.findBySessionId(sessionId);
        
        if (visitorProfile != null) {
            
            // Check if a profile already exists for this userId
            UserProfile userProfile = userProfileMapper.findByUserId(userId);
            
            if (userProfile != null) {
                // If user profile already exists, merge visitor data into it
                // Merge Service Times
                if (userProfile.getServiceTimes() == null) userProfile.setServiceTimes(0);
                userProfile.setServiceTimes(userProfile.getServiceTimes() + (visitorProfile.getServiceTimes() != null ? visitorProfile.getServiceTimes() : 0));
                
                // Merge Satisfaction Score (Simple average of current scores)
                if (visitorProfile.getSatisfactionScore() != null && visitorProfile.getSatisfactionScore() > 0) {
                     double currentScore = userProfile.getSatisfactionScore() != null ? userProfile.getSatisfactionScore() : 0;
                     if (currentScore > 0) {
                         userProfile.setSatisfactionScore((currentScore + visitorProfile.getSatisfactionScore()) / 2);
                     } else {
                         userProfile.setSatisfactionScore(visitorProfile.getSatisfactionScore());
                     }
                }

                saveProfile(userProfile);
                userProfileMapper.deleteById(visitorProfile.getId());
            } else {
                // Main requirement: Update the guest profile directly
                visitorProfile.setUserId(userId);
                visitorProfile.setUserType("REGISTERED");
                saveProfile(visitorProfile);
            }
            
            // Update Logs ownership
            List<ConsultationLog> logs = consultationLogMapper.findBySessionIdOrderByCreateTimeAsc(sessionId);
            for (ConsultationLog log : logs) {
                log.setUserId(userId);
                log.setUserType(1); // Registered User
                if (log.getId() == null) {
                    consultationLogMapper.insert(log);
                } else {
                    consultationLogMapper.update(log);
                }
            }
        }
        
        // Force rebuild/update from orders to ensure Member status is correct based on historical orders
        // This will overwrite fields like totalSpending, purchaseFrequency, userType, etc.
        buildUserProfile(userId);
    }

    public List<UserProfile> searchProfiles(String userType, Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return userProfileMapper.listByConditions(userType, userId, startDate, endDate);
    }

    /**
     * Automatically build or update user profile based on historical data.
     * Task L-02: User profile automatic construction
     */
    @Transactional
    @CacheEvict(value = "userProfile", key = "#userId")
    public UserProfile buildUserProfile(Long userId) {
        List<HistoricalOrder> orders = orderMapper.findByUserId(userId);
        
        UserProfile profile = userProfileMapper.findByUserId(userId);
        if (profile == null) {
            profile = new UserProfile();
        }
        profile.setUserId(userId);

        if (orders.isEmpty()) {
            profile.setTags("新用户");
            profile.setUserType("REGISTERED");
            saveProfile(profile);
            return profile;
        }

        // 1. Calculate Total Spending
        BigDecimal totalSpending = orders.stream()
                .map(HistoricalOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        profile.setTotalSpending(totalSpending);

        // 2. Calculate Purchase Frequency (Orders count for simplicity in MVP)
        profile.setPurchaseFrequency(orders.size());

        // 3. Determine Last Purchase Time
        Optional<LocalDateTime> lastPurchase = orders.stream()
                .map(HistoricalOrder::getCreateTime)
                .max(LocalDateTime::compareTo);
        lastPurchase.ifPresent(profile::setLastPurchaseTime);

        // 4. Analyze Preferred Products (Most frequent product name)
        Map<String, Long> productFrequency = orders.stream()
                .collect(Collectors.groupingBy(HistoricalOrder::getProductName, Collectors.counting()));
        
        String preferredProduct = productFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
        profile.setPreferredProducts(preferredProduct);

        // 5. Generate Tags (L-02 Logic)
        List<String> tags = new ArrayList<>();
        
        // Tag: High Value User
        if (totalSpending.compareTo(new BigDecimal("5000")) > 0) {
            tags.add("高价值用户");
        } else if (totalSpending.compareTo(new BigDecimal("1000")) > 0) {
            tags.add("潜力用户");
        } else {
            tags.add("普通用户");
        }

        // Tag: Loyal User
        if (orders.size() >= 5) {
            tags.add("忠实用户");
        }

        // Tag: Product Preference
        if (!preferredProduct.isEmpty()) {
            tags.add(preferredProduct + "偏好");
        }

        profile.setTags(String.join(",", tags));

        // Determine User Type based on spending
        if (totalSpending.compareTo(BigDecimal.ZERO) > 0) {
            profile.setUserType("MEMBER");
        } else {
            profile.setUserType("REGISTERED");
        }

        // Note: serviceTimes and lastServiceTime would come from ConsultationLog, 
        // skipped for this MVP step or can be added if ConsultationLogRepository is injected.

        saveProfile(profile);
        return profile;
    }

    private void saveProfile(UserProfile profile) {
        if (profile.getId() == null) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.update(profile);
        }
    }
}
