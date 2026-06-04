package com.example.backend.infrastructure.dify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkOrderAnalysisResult {
    private String priority;
    private String tags;
    private String summary;
    private String bizTag;
    private String emotionLevel;
    private BigDecimal dispatchConfidence;
}
