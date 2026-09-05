package com.example.backend.interfaces.config;

import com.example.backend.infrastructure.resilience.ExternalCallRetryPolicy;
import com.example.backend.infrastructure.resilience.RetryableExternalFailure;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Phase4RetryPolicyTest {

    @Test
    void retriesTransientFailureOnlyForIdempotentCall() {
        ExternalCallRetryPolicy policy = new ExternalCallRetryPolicy(3, 0, 0);
        AtomicInteger attempts = new AtomicInteger();

        String result = policy.executeIdempotent("test.read", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new ResourceAccessException("temporary network failure");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void neverRetriesNonIdempotentCall() {
        ExternalCallRetryPolicy policy = new ExternalCallRetryPolicy(3, 0, 0);
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(ResourceAccessException.class, () -> policy.executeNonIdempotent("test.write", () -> {
            attempts.incrementAndGet();
            throw new ResourceAccessException("temporary network failure");
        }));

        assertEquals(1, attempts.get());
    }

    @Test
    void retriesTransientVectorReadMarkedByTheAdapter() {
        ExternalCallRetryPolicy policy = new ExternalCallRetryPolicy(3, 0, 0);
        AtomicInteger attempts = new AtomicInteger();

        String result = policy.executeIdempotent("vector.search", () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new VectorTransientFailure();
            }
            return "vector-result";
        });

        assertEquals("vector-result", result);
        assertEquals(2, attempts.get());
    }

    private static final class VectorTransientFailure extends RuntimeException
            implements RetryableExternalFailure {
        @Override
        public boolean isRetryableExternalFailure() {
            return true;
        }
    }
}
