package com.example.backend.domain.profile.model;

import com.example.backend.domain.shared.model.BaseAggregateRoot;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserProfile extends BaseAggregateRoot {
    private Long id;
    private Long userId;
    private String sessionId;
    private String userType;
    private Double satisfactionScore;
    private String preferredProducts;
    private Integer purchaseFrequency;
    private BigDecimal totalSpending;
    private Integer serviceTimes;
    private LocalDateTime lastPurchaseTime;
    private LocalDateTime lastServiceTime;
    private String tags;

    public boolean isVisitor() {
        return userId == null && sessionId != null;
    }

    public boolean isRegistered() {
        return userId != null;
    }

    public List<String> tagList() {
        if (tags == null || tags.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(tags.split(",")));
    }

    public void addTag(String tag) {
        List<String> current = tagList();
        if (!current.contains(tag)) {
            current.add(tag);
            this.tags = String.join(",", current);
        }
    }

    public void merge(UserProfile visitor) {
        if (visitor.getServiceTimes() != null) {
            this.serviceTimes = (this.serviceTimes != null ? this.serviceTimes : 0)
                    + (visitor.getServiceTimes() != null ? visitor.getServiceTimes() : 0);
        }
        if (visitor.getSatisfactionScore() != null && visitor.getSatisfactionScore() > 0) {
            double current = this.satisfactionScore != null ? this.satisfactionScore : 0;
            this.satisfactionScore = current > 0 ? (current + visitor.getSatisfactionScore()) / 2 : visitor.getSatisfactionScore();
        }
        markUpdated();
    }

    public BigDecimal safeTotalSpending() {
        return totalSpending != null ? totalSpending : BigDecimal.ZERO;
    }
}
