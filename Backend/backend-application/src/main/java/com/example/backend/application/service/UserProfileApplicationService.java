package com.example.backend.application.service;

import com.example.backend.domain.chat.model.ConsultationLog;
import com.example.backend.domain.chat.repository.ConsultationLogRepository;
import com.example.backend.domain.order.model.HistoricalOrder;
import com.example.backend.domain.order.repository.HistoricalOrderRepository;
import com.example.backend.domain.profile.event.UserProfileBuiltEvent;
import com.example.backend.domain.profile.model.UserProfile;
import com.example.backend.domain.profile.repository.UserProfileRepository;
import com.example.backend.domain.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileApplicationService {

    private static final String TAG_ACTIVE_USER = "较活跃用户";
    private static final String STATUS_REGISTERED = "REGISTERED";

    private final UserProfileRepository profileRepository;
    private final ConsultationLogRepository consultationLogRepository;
    private final HistoricalOrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;

    @Cacheable(value = "userProfile", key = "#userId")
    public UserProfile getUserProfile(Long userId) {
        return profileRepository.findByUserId(userId).orElseGet(() -> buildUserProfile(userId));
    }

    @Cacheable(value = "userProfile", key = "#sessionId")
    public UserProfile getOrCreateVisitorProfile(String sessionId) {
        return profileRepository.findBySessionId(sessionId).orElseGet(() -> {
            UserProfile profile = new UserProfile();
            profile.setSessionId(sessionId);
            profile.setUserType("UNREGISTERED");
            profile.setServiceTimes(0);
            profile.setSatisfactionScore(0.0);
            profile.setTags("Visitor");
            return profileRepository.save(profile);
        });
    }

    @CacheEvict(value = "userProfile", key = "#sessionId")
    public void updateVisitorStats(String sessionId) {
        UserProfile profile = getOrCreateVisitorProfile(sessionId);
        List<ConsultationLog> logs = consultationLogRepository.findBySessionIdOrderByCreateTimeAsc(sessionId);
        if (logs.isEmpty()) return;

        profile.setServiceTimes(logs.size());
        profile.setLastServiceTime(logs.get(logs.size() - 1).getCreateTime());

        double avgSat = logs.stream().filter(l -> l.getSatisfaction() != null)
                .mapToInt(ConsultationLog::getSatisfaction).average().orElse(0.0);
        profile.setSatisfactionScore(avgSat);

        if ("UNREGISTERED".equals(profile.getUserType()) && profile.getServiceTimes() > 5) {
            profile.setUserType("HIGH_POTENTIAL");
        }

        List<String> tags = profile.tagList();
        if (!tags.contains("活跃用户") && !tags.contains(TAG_ACTIVE_USER)) {
            if (profile.getServiceTimes() >= 10) tags.add("活跃用户");
            else if (profile.getServiceTimes() >= 5) tags.add(TAG_ACTIVE_USER);
        }
        profile.setTags(String.join(",", tags));
        profileRepository.save(profile);
    }

    @CacheEvict(value = "userProfile", key = "#userId")
    public UserProfile buildUserProfile(Long userId) {
        List<HistoricalOrder> orders = orderRepository.findByUserId(userId);
        UserProfile profile = profileRepository.findByUserId(userId).orElse(new UserProfile());
        profile.setUserId(userId);

        if (orders.isEmpty()) {
            profile.setTags("新用户");
            profile.setUserType(STATUS_REGISTERED);
            return profileRepository.save(profile);
        }

        BigDecimal totalSpending = orders.stream().map(HistoricalOrder::safeTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        profile.setTotalSpending(totalSpending);
        profile.setPurchaseFrequency(orders.size());

        orders.stream().map(HistoricalOrder::getCreateTime).max(LocalDateTime::compareTo)
                .ifPresent(profile::setLastPurchaseTime);

        Map<String, Long> productFreq = orders.stream()
                .collect(Collectors.groupingBy(HistoricalOrder::getProductName, Collectors.counting()));
        String preferred = productFreq.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("");
        profile.setPreferredProducts(preferred);

        List<String> tags = new ArrayList<>();
        if (totalSpending.compareTo(new BigDecimal("5000")) > 0) tags.add("高价值用户");
        else if (totalSpending.compareTo(new BigDecimal("1000")) > 0) tags.add("潜力用户");
        else tags.add("普通用户");

        if (orders.size() >= 5) tags.add("忠实用户");
        if (!preferred.isEmpty()) tags.add(preferred + "偏好");

        long serviceCount = consultationLogRepository.findByUserId(userId).size();
        if (serviceCount >= 10) tags.add("活跃用户");
        else if (serviceCount >= 5) tags.add(TAG_ACTIVE_USER);

        profile.setTags(String.join(",", tags));
        profile.setUserType(totalSpending.compareTo(BigDecimal.ZERO) > 0 ? "MEMBER" : STATUS_REGISTERED);

        UserProfile saved = profileRepository.save(profile);
        eventPublisher.publish(new UserProfileBuiltEvent(userId, saved.getUserType(), saved.getTags()));
        return saved;
    }

    @Transactional
    @CacheEvict(value = "userProfile", allEntries = true)
    public void mergeVisitorToUser(String sessionId, Long userId) {
        profileRepository.findBySessionId(sessionId).ifPresent(visitor -> {
            profileRepository.findByUserId(userId).ifPresentOrElse(userProfile -> {
                userProfile.merge(visitor);
                profileRepository.save(userProfile);
                profileRepository.deleteById(visitor.getId());
            }, () -> {
                visitor.setUserId(userId);
                visitor.setUserType(STATUS_REGISTERED);
                profileRepository.save(visitor);
            });
            List<ConsultationLog> logs = consultationLogRepository.findBySessionIdOrderByCreateTimeAsc(sessionId);
            if (!logs.isEmpty()) {
                List<Long> logIds = logs.stream().map(ConsultationLog::getId).toList();
                consultationLogRepository.batchAssignUser(logIds, userId);
            }
        });
        buildUserProfile(userId);
    }

    public List<UserProfile> searchProfiles(String userType, Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return profileRepository.listByConditions(userType, userId, startDate, endDate);
    }

    @CacheEvict(value = "userProfile", key = "#userId")
    public void updateUserSatisfaction(Long userId, Integer satisfaction) {
        profileRepository.findByUserId(userId).ifPresent(profile -> {
            List<ConsultationLog> logs = consultationLogRepository.findByUserId(userId);
            double avg = logs.stream().filter(l -> l.getSatisfaction() != null)
                    .mapToInt(ConsultationLog::getSatisfaction).average().orElse(satisfaction.doubleValue());
            profile.setSatisfactionScore(avg);
            profileRepository.save(profile);
        });
    }
}
