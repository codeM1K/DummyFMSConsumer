package com.fms.consumer.service;

import com.fms.consumer.integration.OpenRemoteRestClient;
import com.fms.consumer.integration.dto.AuthResponse;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for session credential storage in {@link AuthenticationService}.
 *
 * <p><b>Validates: Requirements 1.4</b></p>
 *
 * <p>Property 2: Session Credential Storage —
 * "For any successful authentication response, the system SHALL store the session
 * credentials and use them in all subsequent API requests without requiring
 * re-authentication until session expiration."</p>
 */
class SessionCredentialStoragePropertyTest {

    private ConfigurationService configService;
    private OpenRemoteRestClient restClient;

    @BeforeProperty
    void setUp() {
        configService = mock(ConfigurationService.class);
        restClient = mock(OpenRemoteRestClient.class);

        when(configService.getClientId()).thenReturn("alamanos-test");
        when(configService.getClientSecret()).thenReturn("hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc");
        when(configService.getRetryMaxAttempts()).thenReturn(3);
        when(configService.getRetryInitialDelay()).thenReturn(1000);
        when(configService.getRetryMaxDelay()).thenReturn(30000);
    }

    /**
     * For any successful authentication response with a session token,
     * {@code isAuthenticated()} returns true.
     *
     * <p><b>Validates: Requirements 1.4</b></p>
     */
    @Property(tries = 100)
    void afterSuccessfulAuth_isAuthenticated_returnsTrue(
            @ForAll("sessionTokens") String token) throws Exception {

        AuthResponse response = new AuthResponse(token, true, null);
        when(restClient.authenticate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(response));

        AuthenticationService service = new AuthenticationService(configService, restClient);
        AuthenticationResult result = service.authenticate().get(5, TimeUnit.SECONDS);

        assertTrue(result.isSuccess(), "Authentication result should be successful");
        assertTrue(service.isAuthenticated(),
                "isAuthenticated() must return true after successful auth with token: " + token);
    }

    /**
     * For any successful authentication response, {@code getSessionToken()} returns
     * the exact token from the response.
     *
     * <p><b>Validates: Requirements 1.4</b></p>
     */
    @Property(tries = 100)
    void afterSuccessfulAuth_getSessionToken_returnsExactToken(
            @ForAll("sessionTokens") String token) throws Exception {

        AuthResponse response = new AuthResponse(token, true, null);
        when(restClient.authenticate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(response));

        AuthenticationService service = new AuthenticationService(configService, restClient);
        service.authenticate().get(5, TimeUnit.SECONDS);

        assertEquals(token, service.getSessionToken(),
                "getSessionToken() must return the exact token from the auth response");
    }

    /**
     * After successful auth, calling {@code authenticate()} again does NOT call the REST
     * client again (returns the cached result immediately).
     *
     * <p><b>Validates: Requirements 1.4</b></p>
     */
    @Property(tries = 50)
    void afterSuccessfulAuth_subsequentCallDoesNotInvokeRestClient(
            @ForAll("sessionTokens") String token) throws Exception {

        AuthResponse response = new AuthResponse(token, true, null);
        when(restClient.authenticate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(response));

        AuthenticationService service = new AuthenticationService(configService, restClient);
        service.authenticate().get(5, TimeUnit.SECONDS);

        // Reset mock to track new invocations
        reset(restClient);

        // Second call should NOT trigger a REST call
        AuthenticationResult secondResult = service.authenticate().get(5, TimeUnit.SECONDS);

        assertTrue(secondResult.isSuccess(), "Second authenticate() should still succeed");
        assertEquals(token, secondResult.getSessionToken(),
                "Second authenticate() should return the cached token");
        verify(restClient, never()).authenticate(anyString(), anyString());
    }

    /**
     * After {@code invalidateSession()}, {@code isAuthenticated()} returns false and
     * {@code getSessionToken()} returns null.
     *
     * <p><b>Validates: Requirements 1.4</b></p>
     */
    @Property(tries = 100)
    void afterInvalidateSession_credentialsAreCleared(
            @ForAll("sessionTokens") String token) throws Exception {

        AuthResponse response = new AuthResponse(token, true, null);
        when(restClient.authenticate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(response));

        AuthenticationService service = new AuthenticationService(configService, restClient);
        service.authenticate().get(5, TimeUnit.SECONDS);

        // Confirm authenticated first
        assertTrue(service.isAuthenticated());
        assertEquals(token, service.getSessionToken());

        // Invalidate
        service.invalidateSession();

        assertFalse(service.isAuthenticated(),
                "isAuthenticated() must return false after invalidateSession()");
        assertNull(service.getSessionToken(),
                "getSessionToken() must return null after invalidateSession()");
    }

    /**
     * For any sequence of auth -> invalidate -> re-auth, the new token is stored correctly.
     *
     * <p><b>Validates: Requirements 1.4</b></p>
     */
    @Property(tries = 50)
    void afterReauthentication_newTokenIsStoredCorrectly(
            @ForAll("sessionTokens") String firstToken,
            @ForAll("sessionTokens") String secondToken) throws Exception {

        Assume.that(!firstToken.equals(secondToken));

        // First auth
        AuthResponse firstResponse = new AuthResponse(firstToken, true, null);
        when(restClient.authenticate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(firstResponse));

        AuthenticationService service = new AuthenticationService(configService, restClient);
        service.authenticate().get(5, TimeUnit.SECONDS);
        assertEquals(firstToken, service.getSessionToken());

        // Invalidate
        service.invalidateSession();
        assertFalse(service.isAuthenticated());

        // Re-auth with new token
        AuthResponse secondResponse = new AuthResponse(secondToken, true, null);
        when(restClient.authenticate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(secondResponse));

        AuthenticationResult reAuthResult = service.authenticate().get(5, TimeUnit.SECONDS);

        assertTrue(reAuthResult.isSuccess(), "Re-authentication should succeed");
        assertEquals(secondToken, service.getSessionToken(),
                "After re-auth, getSessionToken() must return the new token");
        assertTrue(service.isAuthenticated(),
                "After re-auth, isAuthenticated() must return true");
    }

    /**
     * Provides arbitrary session token strings to test with various values.
     * Tokens are non-null, non-empty strings of varying lengths and characters.
     */
    @Provide
    Arbitrary<String> sessionTokens() {
        return Arbitraries.of(
                "abc123",
                "hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc",
                "short",
                "a-very-long-session-token-that-has-many-characters-1234567890",
                "TOKEN_WITH_UNDERSCORES",
                "token-with-dashes",
                "MixedCaseToken123",
                "token.with.dots",
                "token/with/slashes",
                "base64+encoded/token==",
                "uuid-4f7a9b2c-8d3e-41a5-b6c7-2e9f0d1a3b5c",
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0",
                "simple",
                "X",
                "1234567890abcdef"
        );
    }
}
