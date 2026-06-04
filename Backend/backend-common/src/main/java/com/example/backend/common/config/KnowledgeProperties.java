package com.example.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "knowledge.document")
public class KnowledgeProperties {

    private ExpirePolicy expirePolicy = new ExpirePolicy();

    @Data
    public static class ExpirePolicy {
        private boolean enabled = true;
        private int defaultTtlDays = 365;
        private int archiveGraceDays = 30;
        private String autoArchiveCron = "0 0 3 * * ?";
    }
}
