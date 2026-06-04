package com.example.backend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "upload.chunk")
public class UploadProperties {

    private long size = 5 * 1024 * 1024;
    private String tempDir = "./uploads/chunks";
    private int expireHours = 24;
}
