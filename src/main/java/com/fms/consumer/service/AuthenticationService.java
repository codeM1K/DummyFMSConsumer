package com.fms.consumer.service;

import com.fms.consumer.integration.OpenRemoteRestClient;
import com.fms.consumer.integration.dto.AuthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for authenticating with the Open Remote API.
 * Manages session credentials, automatic re-authentication on expiration,
 * and retry logic with exponential backoff.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private volatile String sessionToken;

    private final ConfigurationService configService;
    private final OpenRemoteRestClient restClient;
    private final ScheduledExecutorService scheduler;

    public AuthenticationService(ConfigurationService configService, OpenRemoteRestClient restClient) {
        this.configService = configService;
        this.restClient = restClient;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auth-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Authenticates with the Open Remote API using credentials from ConfigurationService.
     * If already authenticated, returns immediately with the existing token.
     * On failure, retries with exponential backoff (1s, 2s, 4s, 8s... max 30s).
     *
     * @return a CompletableFuture containing the AuthenticationResult
     */
    public CompletableFuture<AuthenticationResult> authenticate() {
        if (isAuthenticated()) {
            log.info("Already authenticated, returning existing session token");
            return CompletableFuture.completedFuture(AuthenticationResult.success(sessionToken));
        }

        String username = configService.getUsername();
        String token = configService.getAuthToken();
        int maxAttempts = configService.getRetryMaxAttempts();
        long initialDelay = configService.getRetryInitialDelay();
        long maxDelay = configService.getRetryMaxDelay();

        log.info("[{}] Starting authentication for user '{}'", Instant.now(), username);

        return authenticateWithRetry(username, token, 1, maxAttempts, initialDelay, maxDelay);
    }

    /**
     * Returns true if the service currently holds a valid session token.
     */
    public boolean isAuthenticated() {
        return sessionToken != null;
    }

    /**
     * Invalidates the current session token and triggers re-authentication.
     */
    public void invalidateSession() {
        log.info("[{}] Session invalidated, triggering re-authentication", Instant.now());
        sessionToken = null;
        scheduleReauthentication();
    }

    /**
     * Returns the current session token, or null if not authenticated.
     */
    public String getSessionToken() {
        return sessionToken;
    }

    /**
     * Schedules a re-authentication attempt after a short delay.
     */
    private void scheduleReauthentication() {
        scheduler.schedule(() -> {
            log.info("[{}] Scheduled re-authentication starting", Instant.now());
            authenticate().whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("[{}] Scheduled re-authentication failed: {}",
                            Instant.now(), throwable.getMessage());
                } else if (result.isSuccess()) {
                    log.info("[{}] Scheduled re-authentication succeeded", Instant.now());
                } else {
                    log.error("[{}] Scheduled re-authentication failed: {}",
                            Instant.now(), result.getErrorMessage());
                }
            });
        }, 1, TimeUnit.SECONDS);
    }

    /**
     * Recursive retry method implementing exponential backoff.
     */
    private CompletableFuture<AuthenticationResult> authenticateWithRetry(
            String username, String token,
            int attempt, int maxAttempts,
            long currentDelay, long maxDelay) {

        log.info("[{}] Authentication attempt {}/{} for user '{}'",
                Instant.now(), attempt, maxAttempts, username);

        return restClient.authenticate(username, token)
                .thenApply(authResponse -> handleSuccess(authResponse))
                .exceptionally(throwable -> {
                    // This returns null to signal we need to retry; handled below
                    log.warn("[{}] Authentication attempt {}/{} failed: {}",
                            Instant.now(), attempt, maxAttempts, throwable.getMessage());
                    return null;
                })
                .thenCompose(result -> {
                    if (result != null && result.isSuccess()) {
                        return CompletableFuture.completedFuture(result);
                    }

                    if (attempt >= maxAttempts) {
                        String errorMsg = String.format(
                                "Authentication failed after %d attempts for user '%s'",
                                maxAttempts, username);
                        log.error("[{}] {}", Instant.now(), errorMsg);
                        return CompletableFuture.completedFuture(AuthenticationResult.failure(errorMsg));
                    }

                    // Schedule retry with exponential backoff
                    long nextDelay = Math.min(currentDelay * 2, maxDelay);
                    log.warn("[{}] Retrying authentication in {}ms (attempt {}/{})",
                            Instant.now(), currentDelay, attempt + 1, maxAttempts);

                    CompletableFuture<AuthenticationResult> retryFuture = new CompletableFuture<>();
                    scheduler.schedule(() -> {
                        authenticateWithRetry(username, token, attempt + 1, maxAttempts, nextDelay, maxDelay)
                                .whenComplete((r, t) -> {
                                    if (t != null) {
                                        retryFuture.completeExceptionally(t);
                                    } else {
                                        retryFuture.complete(r);
                                    }
                                });
                    }, currentDelay, TimeUnit.MILLISECONDS);

                    return retryFuture;
                });
    }

    /**
     * Handles a successful authentication response from the REST client.
     */
    private AuthenticationResult handleSuccess(AuthResponse authResponse) {
        if (authResponse.isSuccess() && authResponse.getSessionToken() != null) {
            this.sessionToken = authResponse.getSessionToken();
            log.info("[{}] Authentication successful, session token stored", Instant.now());
            return AuthenticationResult.success(sessionToken);
        } else {
            String errorMsg = authResponse.getErrorMessage() != null
                    ? authResponse.getErrorMessage()
                    : "Authentication response indicated failure";
            log.warn("[{}] Authentication response not successful: {}", Instant.now(), errorMsg);
            // Return failure so retry logic can kick in
            return AuthenticationResult.failure(errorMsg);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down AuthenticationService scheduler");
        scheduler.shutdownNow();
    }
}
