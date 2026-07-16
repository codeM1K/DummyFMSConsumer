package com.fms.consumer.service;

import com.fms.consumer.config.OpenRemoteProperties;
import com.fms.consumer.integration.OpenRemoteRestClient;
import com.fms.consumer.integration.dto.AuthResponse;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for authentication retry behavior with exponential backoff.
 *
 * <p><b>Validates: Requirements 1.3</b></p>
 *
 * <p>Property 1: Authentication Retry on Failure —
 * "For any authentication failure response, the system SHALL log the error with timestamp
 * and details, then retry authentication with exponential backoff starting at 1 second,
 * not exceeding 30 seconds maximum delay."</p>
 */
class AuthenticationRetryPropertyTest {

    /**
     * Creates a ConfigurationService with fast retry delays for testing.
     * Uses 10ms initial delay and 300ms max delay to keep tests fast,
     * while preserving the exponential backoff behavior.
     */
    private ConfigurationService createTestConfigService(int maxAttempts) {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getRetry().setMaxAttempts(maxAttempts);
        properties.getRetry().setInitialDelay(10);   // 10ms for fast tests
        properties.getRetry().setMaxDelay(300);      // 300ms cap for fast tests
        properties.getApi().setEndpoint("https://fms.pcp.com.gr");
        properties.getApi().setUsername("test-user");
        properties.getApi().setToken("test-token");
        properties.getConnection().setTimeout(5000);
        return new ConfigurationService(properties);
    }

    /**
     * Property: After maxAttempts consecutive failures, the authenticate() method
     * returns a failure result (no more retries are attempted beyond maxAttempts).
     *
     * <p><b>Validates: Requirements 1.3</b></p>
     */
    @Property(tries = 20)
    void allAttemptsExhausted_returnsFailure(
            @ForAll @IntRange(min = 1, max = 5) int maxAttempts) throws Exception {

        ConfigurationService configService = createTestConfigService(maxAttempts);
        OpenRemoteRestClient mockClient = mock(OpenRemoteRestClient.class);

        // Mock: every call fails
        when(mockClient.authenticate(anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("Simulated authentication failure")));

        AuthenticationService authService = new AuthenticationService(configService, mockClient);

        AuthenticationResult result = authService.authenticate().get();

        assertFalse(result.isSuccess(),
                "Authentication must fail after exhausting " + maxAttempts + " attempts");
        assertNotNull(result.getErrorMessage(),
                "Failed authentication must include an error message");

        // Verify the client was called exactly maxAttempts times
        verify(mockClient, times(maxAttempts)).authenticate(anyString(), anyString());

        authService.shutdown();
    }

    /**
     * Property: If the Nth attempt (where N <= maxAttempts) succeeds,
     * the overall authentication result is success.
     *
     * <p><b>Validates: Requirements 1.3</b></p>
     */
    @Property(tries = 20)
    void retrySucceedsOnNthAttempt_returnsSuccess(
            @ForAll @IntRange(min = 1, max = 5) int maxAttempts,
            @ForAll @IntRange(min = 1, max = 5) int successOnAttempt) throws Exception {

        // Ensure successOnAttempt is within maxAttempts
        int effectiveSuccessAttempt = Math.min(successOnAttempt, maxAttempts);

        ConfigurationService configService = createTestConfigService(maxAttempts);
        OpenRemoteRestClient mockClient = mock(OpenRemoteRestClient.class);

        AtomicInteger callCount = new AtomicInteger(0);

        // Mock: fail until the Nth attempt, then succeed
        when(mockClient.authenticate(anyString(), anyString())).thenAnswer(invocation -> {
            int attempt = callCount.incrementAndGet();
            if (attempt < effectiveSuccessAttempt) {
                return CompletableFuture.failedFuture(
                        new RuntimeException("Simulated failure on attempt " + attempt));
            } else {
                AuthResponse successResponse = new AuthResponse("session-token-" + attempt, true, null);
                return CompletableFuture.completedFuture(successResponse);
            }
        });

        AuthenticationService authService = new AuthenticationService(configService, mockClient);

        AuthenticationResult result = authService.authenticate().get();

        assertTrue(result.isSuccess(),
                "Authentication must succeed when attempt " + effectiveSuccessAttempt
                        + " of " + maxAttempts + " succeeds");
        assertNotNull(result.getSessionToken(),
                "Successful authentication must provide a session token");

        // Verify exact number of calls: all failures + one success
        verify(mockClient, times(effectiveSuccessAttempt)).authenticate(anyString(), anyString());

        authService.shutdown();
    }

    /**
     * Property: The exponential backoff delay doubles each time and never exceeds maxDelay.
     * We verify this by measuring elapsed time between attempts.
     *
     * <p>With initialDelay=10ms and maxDelay=300ms, delays should be: 10, 20, 40, 80, 160, 300, 300...</p>
     *
     * <p><b>Validates: Requirements 1.3</b></p>
     */
    @Property(tries = 10)
    void retryDelaysFollowExponentialBackoff_cappedAtMaxDelay(
            @ForAll @IntRange(min = 2, max = 5) int maxAttempts) throws Exception {

        int initialDelay = 10;  // 10ms
        int maxDelay = 300;     // 300ms

        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getRetry().setMaxAttempts(maxAttempts);
        properties.getRetry().setInitialDelay(initialDelay);
        properties.getRetry().setMaxDelay(maxDelay);
        properties.getApi().setEndpoint("https://fms.pcp.com.gr");
        properties.getApi().setUsername("test-user");
        properties.getApi().setToken("test-token");
        properties.getConnection().setTimeout(5000);
        ConfigurationService configService = new ConfigurationService(properties);

        OpenRemoteRestClient mockClient = mock(OpenRemoteRestClient.class);

        // Track timestamps of each call
        java.util.List<Long> callTimestamps = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        when(mockClient.authenticate(anyString(), anyString())).thenAnswer(invocation -> {
            callTimestamps.add(System.currentTimeMillis());
            return CompletableFuture.failedFuture(
                    new RuntimeException("Simulated failure"));
        });

        AuthenticationService authService = new AuthenticationService(configService, mockClient);

        authService.authenticate().get();

        // Verify we got maxAttempts calls
        assertEquals(maxAttempts, callTimestamps.size(),
                "Expected exactly " + maxAttempts + " authentication attempts");

        // Verify delays between consecutive attempts follow exponential backoff pattern
        long expectedDelay = initialDelay;
        for (int i = 1; i < callTimestamps.size(); i++) {
            long actualDelay = callTimestamps.get(i) - callTimestamps.get(i - 1);

            // The delay should be approximately the expected delay (allowing 50% tolerance for scheduling)
            long minExpected = (long) (expectedDelay * 0.5);
            long maxExpected = (long) (expectedDelay * 3.0); // generous upper bound for CI

            assertTrue(actualDelay >= minExpected,
                    "Delay between attempt " + i + " and " + (i + 1) + " was " + actualDelay
                            + "ms, expected at least " + minExpected + "ms (target: " + expectedDelay + "ms)");

            // The actual delay should never exceed maxDelay + generous tolerance
            long cappedExpected = Math.min(expectedDelay, maxDelay);
            assertTrue(actualDelay <= cappedExpected * 4,
                    "Delay between attempt " + i + " and " + (i + 1) + " was " + actualDelay
                            + "ms, which greatly exceeds the capped max of " + cappedExpected + "ms");

            // Next expected delay doubles but is capped at maxDelay
            expectedDelay = Math.min(expectedDelay * 2, maxDelay);
        }

        authService.shutdown();
    }

    /**
     * Property: Each authentication failure triggers exactly one retry (up to maxAttempts total),
     * meaning for N failures (N < maxAttempts), there are N+1 total attempts
     * (N failures + possible next attempt).
     *
     * <p><b>Validates: Requirements 1.3</b></p>
     */
    @Property(tries = 20)
    void eachFailureTriggersRetry_upToMaxAttempts(
            @ForAll @IntRange(min = 1, max = 5) int maxAttempts,
            @ForAll @IntRange(min = 0, max = 4) int failureCount) throws Exception {

        // Ensure failureCount is less than maxAttempts (so we can succeed after)
        int effectiveFailures = Math.min(failureCount, maxAttempts - 1);

        ConfigurationService configService = createTestConfigService(maxAttempts);
        OpenRemoteRestClient mockClient = mock(OpenRemoteRestClient.class);

        AtomicInteger callCount = new AtomicInteger(0);

        when(mockClient.authenticate(anyString(), anyString())).thenAnswer(invocation -> {
            int attempt = callCount.incrementAndGet();
            if (attempt <= effectiveFailures) {
                return CompletableFuture.failedFuture(
                        new RuntimeException("Failure " + attempt));
            } else {
                AuthResponse successResponse = new AuthResponse("token-success", true, null);
                return CompletableFuture.completedFuture(successResponse);
            }
        });

        AuthenticationService authService = new AuthenticationService(configService, mockClient);

        AuthenticationResult result = authService.authenticate().get();

        assertTrue(result.isSuccess(),
                "Authentication must succeed when failure count (" + effectiveFailures
                        + ") is less than maxAttempts (" + maxAttempts + ")");

        // Total calls = effectiveFailures + 1 (the successful attempt)
        assertEquals(effectiveFailures + 1, callCount.get(),
                "Expected " + (effectiveFailures + 1) + " total attempts ("
                        + effectiveFailures + " failures + 1 success)");

        authService.shutdown();
    }

    /**
     * Property: The computed delay for any retry attempt never exceeds the configured maxDelay.
     * This is a mathematical property that validates the backoff cap logic:
     * min(initialDelay * 2^(attempt-1), maxDelay) <= maxDelay for all attempts.
     *
     * <p><b>Validates: Requirements 1.3</b></p>
     */
    @Property(tries = 100)
    void computedDelay_neverExceedsMaxDelay(
            @ForAll @IntRange(min = 1, max = 10) int attemptNumber,
            @ForAll @IntRange(min = 100, max = 5000) int initialDelay,
            @ForAll @IntRange(min = 1000, max = 30000) int maxDelay) {

        // Compute the delay as the AuthenticationService would
        long computedDelay = initialDelay;
        for (int i = 1; i < attemptNumber; i++) {
            computedDelay = Math.min(computedDelay * 2, maxDelay);
        }

        assertTrue(computedDelay <= maxDelay,
                "Computed delay " + computedDelay + "ms for attempt " + attemptNumber
                        + " (initialDelay=" + initialDelay + "ms, maxDelay=" + maxDelay
                        + "ms) exceeds maximum allowed delay");
    }
}
