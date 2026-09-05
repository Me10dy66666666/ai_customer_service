package com.example.backend.infrastructure.dify;

import com.example.backend.infrastructure.resilience.RetryableExternalFailure;

public class DifyApiException extends RuntimeException implements RetryableExternalFailure {

    private final int statusCode;

    public DifyApiException(String message) {
        this(message, null, -1);
    }

    public DifyApiException(String message, Throwable cause) {
        this(message, cause, -1);
    }

    public DifyApiException(String message, int statusCode) {
        this(message, null, statusCode);
    }

    private DifyApiException(String message, Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    @Override
    public boolean isRetryableExternalFailure() {
        return statusCode == 429 || statusCode >= 500;
    }
}
