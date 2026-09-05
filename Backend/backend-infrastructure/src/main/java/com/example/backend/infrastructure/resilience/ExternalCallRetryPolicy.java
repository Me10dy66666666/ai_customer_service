package com.example.backend.infrastructure.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.util.function.Supplier;

/**
 * Small, provider-neutral retry boundary for calls that are explicitly known
 * to be idempotent.  Non-idempotent calls must go through
 * {@link #executeNonIdempotent(String, Supplier)} and are never retried.
 */
@Slf4j
@Component
public class ExternalCallRetryPolicy {

    private final int maxAttempts;
    private final long initialBackoffMs;
    private final long maxBackoffMs;

    public ExternalCallRetryPolicy(
            @Value("${external-call.retry.max-attempts:3}") int maxAttempts,
            @Value("${external-call.retry.initial-backoff-ms:200}") long initialBackoffMs,
            @Value("${external-call.retry.max-backoff-ms:2000}") long maxBackoffMs) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoffMs = Math.max(0, initialBackoffMs);
        this.maxBackoffMs = Math.max(this.initialBackoffMs, maxBackoffMs);
    }

    public <T> T executeIdempotent(String operation, Supplier<T> action) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException failure) {
                lastFailure = failure;
                if (!isRetryable(failure) || attempt == maxAttempts) {
                    throw failure;
                }
                long delayMs = backoffMs(attempt);
                log.debug("Retrying idempotent external call: operation={}, attempt={}, delayMs={}, cause={}",
                        operation, attempt + 1, delayMs, failure.getClass().getSimpleName());
                pause(delayMs);
            }
        }
        throw lastFailure;
    }

    /**
     * Makes the non-idempotent decision explicit at every provider boundary.
     * This method intentionally performs exactly one attempt.
     */
    public <T> T executeNonIdempotent(String operation, Supplier<T> action) {
        return action.get();
    }

    private long backoffMs(int failedAttempt) {
        long exponential;
        try {
            exponential = Math.multiplyExact(initialBackoffMs, 1L << Math.min(failedAttempt - 1, 20));
        } catch (ArithmeticException overflow) {
            exponential = Long.MAX_VALUE;
        }
        return Math.min(maxBackoffMs, exponential);
    }

    private void pause(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ExternalCallRetryInterruptedException(interrupted);
        }
    }

    private boolean isRetryable(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof RetryableExternalFailure marked) {
                if (marked.isRetryableExternalFailure()) {
                    return true;
                }
            }
            if (current instanceof ResourceAccessException) {
                return true;
            }
            if (current instanceof HttpStatusCodeException httpFailure) {
                HttpStatusCode status = httpFailure.getStatusCode();
                return status.value() == 429 || status.is5xxServerError();
            }
            current = current.getCause();
        }
        return false;
    }

    public static class ExternalCallRetryInterruptedException extends RuntimeException {
        public ExternalCallRetryInterruptedException(Throwable cause) {
            super("Interrupted while backing off an external call retry", cause);
        }
    }
}
