package com.fms.consumer.service;

import com.fms.consumer.integration.OpenRemoteRestClient;
import com.fms.consumer.integration.dto.AuthResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthenticationService}.
 * Tests authentication flow, retry logic, session management, and re-authentication.
 *
 * Requirements: 1.3, 1.4, 1.5
 */
class AuthenticationServiceTest {

    @Mock
    private ConfigurationService configService;

    @Mock
    private OpenRemoteRestClient restClient;

    private AuthenticationService authService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        // Configure short retry delays to keep tests fast
        when(configService.getUsername()).thenReturn("test-user");
        when(configService.getAuthToken()).thenReturn("test-token");
        when(configService.getRetryMaxAttempts()).thenReturn(3);
        when(configService.getRetryInitialDelay()).thenReturn(10);
        when(configService.getRetryMaxDelay()).thenReturn(50);

        authService = new AuthenticationService(configService, restClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        authService.shutdown();
        mocks.close();
    }

    // --- Successful Authentication Flow ---

    @Test
    void authenticate_storesSessionToken_onSuccess() throws Exception {
        AuthResponse successResponse = new AuthResponse("session-token-123", true, null);
        when(restClient.authenticate("test-user", "test-token"))
                .thenReturn(CompletableFuture.completedFuture(successResponse));

        AuthenticationResult result = authService.authenticate().get(5, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertEquals("session-token-123", result.getSessionToken());
        assertNull(result.getErrorMessage());
    }

    @Test
    void isAuthenticated_returnsFalse_initially() {
        assertFalse(authService.isAuthenticated());
    }

    @Test
    void isAuthenticated_returnsTrue_afterSuccessfulAuthentication() throws Exception {
        AuthResponse successResponse = new AuthResponse("session-token-abc", true, null);
        when(restClient.authenticate("test-user", "test-token"))
                .thenReturn(CompletableFuture.completedFuture(successResponse));

        authService.authenticate().get(5, TimeUnit.SECONDS);

        assertTrue(authService.isAuthenticated());
    }

    @Test
    void authenticate_returnsImmediately_whenAlreadyAuthenticated() throws Exception {
        // First, authenticate successfully
        AuthResponse successResponse = new AuthResponse("existing-token", true, null);
        when(restClient.authenticate("test-user", "test-token"))
                .thenReturn(CompletableFuture.completedFuture(successResponse));

        authService.authenticate().get(5, TimeUnit.SECONDS);

        // Second call should not hit the REST client again
        AuthenticationResult result = authService.authenticate().get(5, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertEquals("existing-token", result.getSessionToken());
        // authenticate on restClient should have been called exactly once
        verify(restClient, times(1)).authenticate(anyString(), anyString());
    }

    // --- Authentication Failure After Max Retries ---

    @Test
    void authenticate_returnsFailure_afterMaxRetries_whenApiThrowsException() throws Exception {
        when(restClient.authenticate("test-user", "test-token"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Connection refused")));

        AuthenticationResult result = authService.authenticate().get(5, TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("failed after 3 attempts"));
        assertFalse(authService.isAuthenticated());
    }

    @Test
    void authenticate_returnsFailure_afterMaxRetries_whenResponseIndicatesFailure() throws Exception {
        AuthResponse failResponse = new AuthResponse(null, false, "Invalid credentials");
        when(restClient.authenticate("test-user", "test-token"))
                .thenReturn(CompletableFuture.completedFuture(failResponse));

        AuthenticationResult result = authService.authenticate().get(5, TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("failed after 3 attempts"));
    }

    @Test
    void authenticate_retriesBeforeFailing() throws Exception {
        when(restClient.authenticate("test-user", "test-token"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Network error")));

        authService.authenticate().get(5, TimeUnit.SECONDS);

        // Should have attempted 3 times (maxAttempts = 3)
        verify(restClient, times(3)).authenticate("test-user", "test-token");
    }

    @Test
    void authenticate_succeedsOnRetry_afterInitialFailure() throws Exception {
        AuthResponse successResponse = new AuthResponse("retry-token", true, null);

        // First call fails, second call succeeds
        when(restClient.authenticate("test-user", "test-token"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Temporary failure")))
                .thenReturn(CompletableFuture.completedFuture(successResponse));

        AuthenticationResult result = authService.authenticate().get(5, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertEquals("retry-token", result.getSessionToken());
        verify(restClient, times(2)).authenticate("test-user", "test-token");
    }

    // --- Session Invalidation and Re-authentication ---

    @Test
    void invalidateSession_setsSessionTokenToNull() throws Exception {
        // First authenticate
        AuthResponse successResponse = new AuthResponse("token-to-invalidate", true, null);
        when(restClient.authenticate("test-user", "test-token"))
                .thenReturn(CompletableFuture.completedFuture(successResponse));

        authService.authenticate().get(5, TimeUnit.SECONDS);
        assertTrue(authService.isAuthenticated());

        // Invalidate
        authService.invalidateSession();
        assertFalse(authService.isAuthenticated());
        assertNull(authService.getSessionToken());
    }

    @Test
    void authenticate_makesNewApiRequest_afterSessionInvalidation() throws Exception {
        AuthResponse firstResponse = new AuthResponse("first-token", true, null);
        AuthResponse secondResponse = new AuthResponse("second-token", true, null);

        when(restClient.authenticate("test-user", "test-token"))
                .thenReturn(CompletableFuture.completedFuture(firstResponse))
                .thenReturn(CompletableFuture.completedFuture(secondResponse));

        // First authentication
        authService.authenticate().get(5, TimeUnit.SECONDS);
        assertEquals("first-token", authService.getSessionToken());

        // Invalidate session
        authService.invalidateSession();
        assertNull(authService.getSessionToken());

        // Re-authenticate - should make a new API call
        AuthenticationResult result = authService.authenticate().get(5, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertEquals("second-token", result.getSessionToken());
        // Two authenticate calls should have been made to the rest client
        verify(restClient, times(2)).authenticate("test-user", "test-token");
    }

    @Test
    void invalidateSession_triggersScheduledReauthentication() throws Exception {
        AuthResponse response = new AuthResponse("reauth-token", true, null);
        when(restClient.authenticate("test-user", "test-token"))
                .thenReturn(CompletableFuture.completedFuture(response));

        // Authenticate first
        authService.authenticate().get(5, TimeUnit.SECONDS);

        // Reset the mock to track subsequent calls
        reset(restClient);
        when(restClient.authenticate("test-user", "test-token"))
                .thenReturn(CompletableFuture.completedFuture(response));

        // Invalidate - should schedule re-authentication after 1 second
        authService.invalidateSession();

        // Wait for the scheduled re-authentication (scheduled at 1 second delay)
        Thread.sleep(1500);

        // The scheduler should have triggered a new authenticate call
        verify(restClient, atLeastOnce()).authenticate("test-user", "test-token");
    }

    // --- AuthenticationResult Factory Methods ---

    @Test
    void authenticationResult_success_createsSuccessResult() {
        AuthenticationResult result = AuthenticationResult.success("my-token");

        assertTrue(result.isSuccess());
        assertEquals("my-token", result.getSessionToken());
        assertNull(result.getErrorMessage());
    }

    @Test
    void authenticationResult_failure_createsFailureResult() {
        AuthenticationResult result = AuthenticationResult.failure("Something went wrong");

        assertFalse(result.isSuccess());
        assertNull(result.getSessionToken());
        assertEquals("Something went wrong", result.getErrorMessage());
    }

    @Test
    void authenticationResult_success_withNullToken() {
        AuthenticationResult result = AuthenticationResult.success(null);

        assertTrue(result.isSuccess());
        assertNull(result.getSessionToken());
    }

    @Test
    void authenticationResult_failure_withNullMessage() {
        AuthenticationResult result = AuthenticationResult.failure(null);

        assertFalse(result.isSuccess());
        assertNull(result.getErrorMessage());
    }

    // --- Edge Cases ---

    @Test
    void getSessionToken_returnsNull_whenNotAuthenticated() {
        assertNull(authService.getSessionToken());
    }

    @Test
    void getSessionToken_returnsToken_afterAuthentication() throws Exception {
        AuthResponse successResponse = new AuthResponse("stored-token", true, null);
        when(restClient.authenticate("test-user", "test-token"))
                .thenReturn(CompletableFuture.completedFuture(successResponse));

        authService.authenticate().get(5, TimeUnit.SECONDS);

        assertEquals("stored-token", authService.getSessionToken());
    }
}
