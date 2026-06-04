package com.example.backend.infrastructure.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "ocr")
public class OcrProperties {

    private String engine = "aliyun";
    private double confidenceThreshold = 0.85;
    private int timeoutMs = 10000;
    private int maxConcurrency = 4;
    private String defaultLanguage = "zh-CN";
    private List<String> languages = new ArrayList<>();

    private Aliyun aliyun = new Aliyun();

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }

    public double getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(double confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public int getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }

    public String getDefaultLanguage() { return defaultLanguage; }
    public void setDefaultLanguage(String defaultLanguage) { this.defaultLanguage = defaultLanguage; }

    public List<String> getLanguages() { return languages; }
    public void setLanguages(List<String> languages) { this.languages = languages; }

    public Aliyun getAliyun() { return aliyun; }
    public void setAliyun(Aliyun aliyun) { this.aliyun = aliyun; }

    public static class Aliyun {
        private String accessKeyId;
        private String accessKeySecret;
        private String endpoint = "ocr-api.cn-hangzhou.aliyuncs.com";
        private String region = "cn-hangzhou";
        private Api api = new Api();

        public String getAccessKeyId() { return accessKeyId; }
        public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }

        public String getAccessKeySecret() { return accessKeySecret; }
        public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }

        public Api getApi() { return api; }
        public void setApi(Api api) { this.api = api; }
    }

    public static class Api {
        private String recognizeAdvanced = "RecognizeAdvanced";
        private String table = "RecognizeTable";
        private String documentStructure = "RecognizeDocumentStructure";

        public String getRecognizeAdvanced() { return recognizeAdvanced; }
        public void setRecognizeAdvanced(String recognizeAdvanced) { this.recognizeAdvanced = recognizeAdvanced; }

        public String getTable() { return table; }
        public void setTable(String table) { this.table = table; }

        public String getDocumentStructure() { return documentStructure; }
        public void setDocumentStructure(String documentStructure) { this.documentStructure = documentStructure; }
    }
}
