package com.fms.consumer.service;

import com.fms.consumer.config.OpenRemoteProperties;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for {@link ConfigurationService}.
 *
 * <p><b>Validates: Requirements 13.1</b></p>
 *
 * <p>Property 38: API Endpoint Configuration Reading —
 * "For any valid API endpoint URL provided in configuration, the system SHALL read
 * and use that endpoint for API communication."</p>
 */
class ConfigurationServicePropertyTest {

    private static final String DEFAULT_ENDPOINT = "https://fms.pcp.com.gr";

    /**
     * Property 38: For any valid URL configured as the API endpoint,
     * the ConfigurationService SHALL return that exact URL verbatim.
     *
     * <p><b>Validates: Requirements 13.1</b></p>
     */
    @Property(tries = 200)
    void validUrlEndpoint_isReturnedVerbatim(@ForAll("validUrls") String validUrl) {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getApi().setEndpoint(validUrl);
        ConfigurationService service = new ConfigurationService(properties);

        String result = service.getApiEndpoint();

        assertEquals(validUrl, result,
                "ConfigurationService must return the configured valid URL verbatim, but got: " + result);
    }

    /**
     * Property 38 (negative case): For any invalid URL string configured as the API endpoint,
     * the ConfigurationService SHALL fall back to the default endpoint.
     *
     * <p><b>Validates: Requirements 13.1</b></p>
     */
    @Property(tries = 200)
    void invalidUrlEndpoint_fallsBackToDefault(@ForAll("invalidUrls") String invalidUrl) {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getApi().setEndpoint(invalidUrl);
        ConfigurationService service = new ConfigurationService(properties);

        String result = service.getApiEndpoint();

        assertEquals(DEFAULT_ENDPOINT, result,
                "ConfigurationService must fall back to default for invalid URL: '" + invalidUrl + "', but got: " + result);
    }

    /**
     * Generates various valid URL strings with different schemes, hosts, ports, and paths.
     */
    @Provide
    Arbitrary<String> validUrls() {
        Arbitrary<String> schemes = Arbitraries.of("http", "https");
        Arbitrary<String> hosts = Arbitraries.of(
                "localhost",
                "example.com",
                "api.fleet.io",
                "192.168.1.100",
                "10.0.0.1",
                "my-server.internal.net",
                "fms.pcp.com.gr",
                "openremote.company.org"
        );
        Arbitrary<String> ports = Arbitraries.of("", ":8080", ":443", ":3000", ":9090", ":80");
        Arbitrary<String> paths = Arbitraries.of(
                "",
                "/",
                "/api",
                "/api/v1",
                "/api/v2/fleet",
                "/openremote/rest",
                "/swagger"
        );

        return Combinators.combine(schemes, hosts, ports, paths)
                .as((scheme, host, port, path) -> scheme + "://" + host + port + path);
    }

    /**
     * Generates various invalid URL strings that should trigger fallback to default.
     */
    @Provide
    Arbitrary<String> invalidUrls() {
        return Arbitraries.of(
                "not-a-url",
                "just-text",
                "ftp-missing-slashes:example.com",
                "://missing-scheme",
                "htp://typo-scheme.com",
                "random string with spaces",
                "12345",
                "file-without-protocol",
                "@#$%^&*",
                "httpx://unsupported-scheme.com"
        );
    }
}
