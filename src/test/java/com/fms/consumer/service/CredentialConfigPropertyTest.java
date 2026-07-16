package com.fms.consumer.service;

import com.fms.consumer.config.OpenRemoteProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for credential configuration reading.
 *
 * <p><b>Property 39: Authentication Credentials Configuration Reading</b></p>
 * <p>For any valid authentication credentials provided in configuration,
 * the system SHALL read and use those credentials for authentication.</p>
 *
 * <p><b>Validates: Requirements 13.2</b></p>
 *
 * <p>Tests the following properties:</p>
 * <ul>
 *   <li>For ANY non-blank username string configured, ConfigurationService.getUsername() SHALL return that exact string.</li>
 *   <li>For ANY non-blank token string configured, ConfigurationService.getAuthToken() SHALL return that exact string.</li>
 *   <li>For blank/null credentials, the service SHALL fall back to defaults.</li>
 * </ul>
 */
class CredentialConfigPropertyTest {

    private static final String DEFAULT_USERNAME = "";
    private static final String DEFAULT_TOKEN = "";

    private OpenRemoteProperties properties;
    private ConfigurationService service;

    @BeforeEach
    void setUp() {
        properties = new OpenRemoteProperties();
        service = new ConfigurationService(properties);
    }

    // --- Username Property Tests ---

    /**
     * Property: For ANY non-blank username string configured,
     * ConfigurationService.getUsername() SHALL return that exact string.
     */
    @ParameterizedTest(name = "getUsername returns configured username verbatim: \"{0}\"")
    @MethodSource("validUsernameStrings")
    void getUsername_returnsConfiguredValue_forAnyNonBlankUsername(String username) {
        properties.getApi().setUsername(username);
        assertEquals(username, service.getUsername(),
                "ConfigurationService must return the configured username verbatim");
    }

    /**
     * Property: For blank or null usernames, the service SHALL fall back to default.
     */
    @ParameterizedTest(name = "getUsername falls back to default for blank/null: \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "  \t\n  "})
    void getUsername_fallsBackToDefault_forBlankOrNullUsername(String invalidUsername) {
        properties.getApi().setUsername(invalidUsername);
        assertEquals(DEFAULT_USERNAME, service.getUsername(),
                "ConfigurationService must fall back to default username for blank/null values");
    }

    // --- Auth Token Property Tests ---

    /**
     * Property: For ANY non-blank token string configured,
     * ConfigurationService.getAuthToken() SHALL return that exact string.
     */
    @ParameterizedTest(name = "getAuthToken returns configured token verbatim: \"{0}\"")
    @MethodSource("validTokenStrings")
    void getAuthToken_returnsConfiguredValue_forAnyNonBlankToken(String token) {
        properties.getApi().setToken(token);
        assertEquals(token, service.getAuthToken(),
                "ConfigurationService must return the configured auth token verbatim");
    }

    /**
     * Property: For blank or null tokens, the service SHALL fall back to default.
     */
    @ParameterizedTest(name = "getAuthToken falls back to default for blank/null: \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "  \t\n  "})
    void getAuthToken_fallsBackToDefault_forBlankOrNullToken(String invalidToken) {
        properties.getApi().setToken(invalidToken);
        assertEquals(DEFAULT_TOKEN, service.getAuthToken(),
                "ConfigurationService must fall back to default token for blank/null values");
    }

    // --- Data Providers ---

    /**
     * Provides a variety of valid username strings to test the property across
     * diverse inputs: alphanumeric, with special characters, different lengths,
     * unicode characters, and edge cases.
     */
    static Stream<String> validUsernameStrings() {
        return Stream.of(
                // Simple alphanumeric
                "admin",
                "user123",
                "testUser",
                // Short usernames
                "a",
                "ab",
                // Long usernames
                "a-very-long-username-that-exceeds-typical-length-limits-for-testing",
                "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                // Special characters
                "user@domain.com",
                "user-with-dashes",
                "user_with_underscores",
                "user.with.dots",
                "user+tag",
                // Mixed case
                "AlamanosTest",
                "ALLCAPS",
                // Numeric-only
                "123456",
                "007",
                // Credentials with spaces in the middle (non-blank)
                "user name",
                " leading-space",
                "trailing-space ",
                // Special characters used in passwords/tokens
                "p@$$w0rd!",
                "key=value&foo=bar",
                "user/path",
                // Unicode characters
                "\u03B1\u03BB\u03B1\u03BC\u03AC\u03BD\u03BF\u03C2",
                "caf\u00E9-user",
                // Default value should also work when explicitly set
                "test-client-id"
        );
    }

    /**
     * Provides a variety of valid token strings to test the property across
     * diverse inputs: alphanumeric tokens, API keys, JWTs, special chars, etc.
     */
    static Stream<String> validTokenStrings() {
        return Stream.of(
                // Simple alphanumeric tokens
                "abc123",
                "simpleToken",
                // Short tokens
                "x",
                "tk",
                // Long tokens (API key style)
                "someRandomLongTokenValueForTestingPurposesExtraLongSuffix1234567890",
                "a".repeat(256),
                // UUID-style tokens
                "550e8400-e29b-41d4-a716-446655440000",
                // Base64-encoded token style
                "dXNlcjpwYXNzd29yZA==",
                "SGVsbG8gV29ybGQh",
                // JWT-like tokens (header.payload.signature)
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.signature",
                // Tokens with special characters
                "token+with+plus",
                "token/with/slashes",
                "token=with=equals",
                "token&with&ampersands",
                "t0k3n!@#$%^",
                // Tokens with spaces in the middle (non-blank)
                "token with spaces",
                " leading-space-token",
                "trailing-space-token ",
                // Numeric tokens
                "1234567890",
                "9999999999999999",
                // Mixed case tokens
                "AbCdEfGhIjKlMnOp",
                "ALL_CAPS_TOKEN",
                "all_lower_token",
                // Default token value should work when explicitly set
                "test-client-secret"
        );
    }
}
