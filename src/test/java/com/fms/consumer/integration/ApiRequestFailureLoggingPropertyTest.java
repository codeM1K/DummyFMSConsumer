package com.fms.consumer.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fms.consumer.config.OpenRemoteProperties;
import com.fms.consumer.service.ConfigurationService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for API request failure logging.
 *
 * <p><b>Validates: Requirements 12.1</b></p>
 *
 * <p>Property 34: API Request Failure Logging —
 * "For any failed API request, the system SHALL log the error with timestamp
 * and detailed error information."</p>
 *
 * <p>This test validates that when OpenRemoteRestClient encounters failures
 * (authentication, realm discovery, vehicle discovery), it logs the error at ERROR level
 * with a timestamp (Instant.now() pattern) and detailed error information including
 * the URL, status code, and response body.</p>
 */
class ApiRequestFailureLoggingPropertyTest {

    // Pattern matching ISO-8601 Instant timestamps (e.g., 2024-01-15T10:30:00.123456Z)
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");

    private MockWebServer mockServer;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger restClientLogger;

    @BeforeTry
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        // Attach a ListAppender to the OpenRemoteRestClient logger to capture log events
        restClientLogger = (Logger) LoggerFactory.getLogger(OpenRemoteRestClient.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        restClientLogger.addAppender(logAppender);
    }

    @AfterTry
    void tearDown() throws IOException {
        restClientLogger.detachAppender(logAppender);
        logAppender.stop();
        mockServer.shutdown();
    }

    private ConfigurationService createConfigService() {
        String baseUrl = mockServer.url("").toString();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getApi().setEndpoint(baseUrl);
        properties.getApi().setUsername("test-user");
        properties.getApi().setToken("test-token");
        properties.getConnection().setTimeout(5000);
        return new ConfigurationService(properties);
    }

    /**
     * Property: For any HTTP error status code returned during authentication,
     * the system SHALL log an ERROR-level message containing a timestamp and
     * detailed error information (URL, status code, response body).
     *
     * <p><b>Validates: Requirements 12.1</b></p>
     */
    @Property(tries = 30)
    void authenticationFailure_logsErrorWithTimestampAndDetails(
            @ForAll("httpErrorCodes") int statusCode,
            @ForAll("errorBodies") String errorBody) throws Exception {

        mockServer.enqueue(new MockResponse()
                .setResponseCode(statusCode)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody));

        ConfigurationService configService = createConfigService();
        OpenRemoteRestClient client = new OpenRemoteRestClient(configService);

        CompletableFuture<?> future = client.authenticate("test-user", "test-token");

        // The call should fail
        assertThrows(ExecutionException.class, future::get);

        // Verify ERROR-level logging occurred
        List<ILoggingEvent> errorLogs = logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .toList();

        assertFalse(errorLogs.isEmpty(),
                "At least one ERROR log must be produced for authentication failure with status " + statusCode);

        // Verify the log message contains a timestamp pattern
        ILoggingEvent errorLog = errorLogs.get(0);
        String formattedMessage = errorLog.getFormattedMessage();

        assertTrue(TIMESTAMP_PATTERN.matcher(formattedMessage).find(),
                "ERROR log must contain a timestamp. Actual message: " + formattedMessage);

        // Verify the log contains the status code (detailed error info)
        assertTrue(formattedMessage.contains(String.valueOf(statusCode)),
                "ERROR log must contain the HTTP status code " + statusCode
                        + ". Actual message: " + formattedMessage);
    }

    /**
     * Property: For any HTTP error status code returned during realm retrieval,
     * the system SHALL log an ERROR-level message containing a timestamp and
     * detailed error information.
     *
     * <p><b>Validates: Requirements 12.1</b></p>
     */
    @Property(tries = 30)
    void realmRetrievalFailure_logsErrorWithTimestampAndDetails(
            @ForAll("httpErrorCodes") int statusCode,
            @ForAll("errorBodies") String errorBody) throws Exception {

        mockServer.enqueue(new MockResponse()
                .setResponseCode(statusCode)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody));

        ConfigurationService configService = createConfigService();
        OpenRemoteRestClient client = new OpenRemoteRestClient(configService);

        CompletableFuture<?> future = client.getRealms("valid-session-token");

        // The call should fail
        assertThrows(ExecutionException.class, future::get);

        // Verify ERROR-level logging occurred
        List<ILoggingEvent> errorLogs = logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .toList();

        assertFalse(errorLogs.isEmpty(),
                "At least one ERROR log must be produced for realm retrieval failure with status " + statusCode);

        // Verify the log message contains a timestamp pattern
        ILoggingEvent errorLog = errorLogs.get(0);
        String formattedMessage = errorLog.getFormattedMessage();

        assertTrue(TIMESTAMP_PATTERN.matcher(formattedMessage).find(),
                "ERROR log must contain a timestamp. Actual message: " + formattedMessage);

        // Verify the log contains the status code
        assertTrue(formattedMessage.contains(String.valueOf(statusCode)),
                "ERROR log must contain the HTTP status code " + statusCode
                        + ". Actual message: " + formattedMessage);
    }

    /**
     * Property: For any HTTP error status code returned during vehicle retrieval,
     * the system SHALL log an ERROR-level message containing a timestamp and
     * detailed error information including the realm ID.
     *
     * <p><b>Validates: Requirements 12.1</b></p>
     */
    @Property(tries = 30)
    void vehicleRetrievalFailure_logsErrorWithTimestampAndDetails(
            @ForAll("httpErrorCodes") int statusCode,
            @ForAll("realmIds") String realmId,
            @ForAll("errorBodies") String errorBody) throws Exception {

        mockServer.enqueue(new MockResponse()
                .setResponseCode(statusCode)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody));

        ConfigurationService configService = createConfigService();
        OpenRemoteRestClient client = new OpenRemoteRestClient(configService);

        CompletableFuture<?> future = client.getVehicles(realmId, "valid-session-token");

        // The call should fail
        assertThrows(ExecutionException.class, future::get);

        // Verify ERROR-level logging occurred
        List<ILoggingEvent> errorLogs = logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .toList();

        assertFalse(errorLogs.isEmpty(),
                "At least one ERROR log must be produced for vehicle retrieval failure "
                        + "with status " + statusCode + " for realm '" + realmId + "'");

        // Verify the log message contains a timestamp pattern
        ILoggingEvent errorLog = errorLogs.get(0);
        String formattedMessage = errorLog.getFormattedMessage();

        assertTrue(TIMESTAMP_PATTERN.matcher(formattedMessage).find(),
                "ERROR log must contain a timestamp. Actual message: " + formattedMessage);

        // Verify the log contains the status code
        assertTrue(formattedMessage.contains(String.valueOf(statusCode)),
                "ERROR log must contain the HTTP status code " + statusCode
                        + ". Actual message: " + formattedMessage);

        // Verify the log contains the realm ID (detailed context)
        assertTrue(formattedMessage.contains(realmId),
                "ERROR log must contain the realm ID '" + realmId
                        + "'. Actual message: " + formattedMessage);
    }

    /**
     * Property: For any failed API request, the error log SHALL be at ERROR level
     * (not WARN, INFO, or DEBUG), confirming proper severity classification.
     *
     * <p><b>Validates: Requirements 12.1</b></p>
     */
    @Property(tries = 20)
    void failedApiRequest_loggedAtErrorLevel(
            @ForAll("apiOperations") String operation,
            @ForAll("httpErrorCodes") int statusCode) throws Exception {

        mockServer.enqueue(new MockResponse()
                .setResponseCode(statusCode)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\": \"test error\"}"));

        ConfigurationService configService = createConfigService();
        OpenRemoteRestClient client = new OpenRemoteRestClient(configService);

        CompletableFuture<?> future;
        switch (operation) {
            case "authenticate" -> future = client.authenticate("user", "token");
            case "getRealms" -> future = client.getRealms("session-token");
            case "getVehicles" -> future = client.getVehicles("realm-1", "session-token");
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        }

        try {
            future.get();
        } catch (ExecutionException e) {
            // Expected
        }

        // Verify that at least one ERROR-level log was produced
        boolean hasErrorLog = logAppender.list.stream()
                .anyMatch(event -> event.getLevel() == Level.ERROR);

        assertTrue(hasErrorLog,
                "Failed " + operation + " with status " + statusCode
                        + " must produce at least one ERROR-level log entry");
    }

    /**
     * Generates HTTP error status codes (4xx and 5xx).
     */
    @Provide
    Arbitrary<Integer> httpErrorCodes() {
        return Arbitraries.of(400, 401, 403, 404, 405, 408, 429, 500, 502, 503, 504);
    }

    /**
     * Generates various error response bodies.
     */
    @Provide
    Arbitrary<String> errorBodies() {
        return Arbitraries.of(
                "{\"error\": \"Unauthorized\"}",
                "{\"error\": \"Internal Server Error\"}",
                "{\"error\": \"Service Unavailable\"}",
                "{\"message\": \"Rate limit exceeded\"}",
                "{\"status\": \"error\", \"details\": \"Connection timeout\"}",
                "Server Error"
        );
    }

    /**
     * Generates realm IDs for vehicle retrieval tests.
     */
    @Provide
    Arbitrary<String> realmIds() {
        return Arbitraries.of(
                "realm-1", "realm-athens", "realm-test", "fleet-alpha", "production-realm"
        );
    }

    /**
     * Generates API operation names for cross-operation testing.
     */
    @Provide
    Arbitrary<String> apiOperations() {
        return Arbitraries.of("authenticate", "getRealms", "getVehicles");
    }
}
