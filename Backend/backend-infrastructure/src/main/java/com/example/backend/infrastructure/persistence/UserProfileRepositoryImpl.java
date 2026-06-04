package com.example.backend.infrastructure.persistence;

import com.example.backend.domain.profile.model.UserProfile;
import com.example.backend.domain.profile.repository.UserProfileRepository;
import com.example.backend.infrastructure.persistence.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class UserProfileRepositoryImpl implements UserProfileRepository {
    private final UserProfileMapper mapper;

    private static final String ALL_TYPE = "All";
    private static final String MEMBER_TYPE = "MEMBER";
    private static final String REGISTERED_TYPE = "REGISTERED";

    @Override
    public UserProfile save(UserProfile profile) {
        com.example.backend.infrastructure.persistence.entity.UserProfile po = toEntity(profile);
        if (profile.getId() == null) mapper.insert(po); else mapper.update(po);
        return toDomain(mapper.selectById(po.getId()));
    }

    @Override public void deleteById(Long id) { mapper.deleteById(id); }

    @Override public Optional<UserProfile> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }
    @Override public Optional<UserProfile> findByUserId(Long userId) {
        return Optional.ofNullable(mapper.findByUserId(userId)).map(this::toDomain);
    }
    @Override public Optional<UserProfile> findBySessionId(String sessionId) {
        return Optional.ofNullable(mapper.findBySessionId(sessionId)).map(this::toDomain);
    }
    @Override public List<UserProfile> listByConditions(String userType, Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return mapper.listByConditions(userType, userId, startDate, endDate, ALL_TYPE, MEMBER_TYPE, REGISTERED_TYPE).stream().map(this::toDomain).collect(Collectors.toList());
    }

    private UserProfile toDomain(com.example.backend.infrastructure.persistence.entity.UserProfile po) {
        UserProfile p = new UserProfile();
        p.setId(po.getId()); p.setUserId(po.getUserId()); p.setSessionId(po.getSessionId());
        p.setUserType(po.getUserType()); p.setSatisfactionScore(po.getSatisfactionScore());
        p.setPreferredProducts(po.getPreferredProducts()); p.setPurchaseFrequency(po.getPurchaseFrequency());
        p.setTotalSpending(po.getTotalSpending()); p.setServiceTimes(po.getServiceTimes());
        p.setLastPurchaseTime(po.getLastPurchaseTime()); p.setLastServiceTime(po.getLastServiceTime());
        p.setTags(po.getTags()); p.setUpdateTime(po.getUpdateTime());
        return p;
    }

    private com.example.backend.infrastructure.persistence.entity.UserProfile toEntity(UserProfile p) {
        com.example.backend.infrastructure.persistence.entity.UserProfile po = new com.example.backend.infrastructure.persistence.entity.UserProfile();
        po.setId(p.getId()); po.setUserId(p.getUserId()); po.setSessionId(p.getSessionId());
        po.setUserType(p.getUserType()); po.setSatisfactionScore(p.getSatisfactionScore());
        po.setPreferredProducts(p.getPreferredProducts()); po.setPurchaseFrequency(p.getPurchaseFrequency());
        po.setTotalSpending(p.getTotalSpending()); po.setServiceTimes(p.getServiceTimes());
        po.setLastPurchaseTime(p.getLastPurchaseTime()); po.setLastServiceTime(p.getLastServiceTime());
        po.setTags(p.getTags());
        return po;
    }
}
