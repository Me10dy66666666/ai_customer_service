package com.example.backend.infrastructure.resilience;

/**
 * Optional marker for an external-call failure whose status is known to be
 * transient.  The retry policy deliberately depends on this small contract
 * rather than on a concrete provider client.
 */
public interface RetryableExternalFailure {

    boolean isRetryableExternalFailure();
}
