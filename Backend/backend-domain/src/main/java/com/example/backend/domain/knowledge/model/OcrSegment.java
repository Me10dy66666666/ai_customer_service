package com.example.backend.domain.knowledge.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OcrSegment {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_UNCERTAIN = "UNCERTAIN";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_REVIEWED = "REVIEWED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    private Long id;
    private Long documentId;
    private Integer segmentIndex;
    private String ocrText;
    private String reviewedText;
    private Double confidence;
    private String boundingBox;
    private String status;
    private LocalDateTime createdAt;

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public boolean isUncertain() {
        return STATUS_UNCERTAIN.equals(status);
    }

    public boolean isConfirmed() {
        return STATUS_CONFIRMED.equals(status);
    }

    public void markPending() {
        this.status = STATUS_PENDING;
    }

    public void confirm() {
        this.status = STATUS_CONFIRMED;
    }

    public void review(String correctedText) {
        this.reviewedText = correctedText;
        this.status = STATUS_REVIEWED;
    }

    public void skip() {
        this.status = STATUS_SKIPPED;
    }

    public String getDisplayText() {
        if (STATUS_REVIEWED.equals(status) && reviewedText != null) {
            return reviewedText;
        }
        return ocrText;
    }
}
