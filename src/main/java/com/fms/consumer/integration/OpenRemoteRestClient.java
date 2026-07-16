package com.fms.consumer.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fms.consumer.integration.dto.AuthResponse;
import com.fms.consumer.integration.dto.RealmDTO;
import com.fms.consumer.integration.dto.VehicleDTO;
import com.fms.consumer.service.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST client for communicating with the Open Remote API.
 * Handles authentication, realm discovery, and vehicle retrieval.
 */
@Component
public class OpenRemoteRestClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRemoteRestClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ConfigurationService configurationService;

    public OpenRemoteRestClient(ConfigurationService configurationService) {
        this(configurationService, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(configurationService.getConnectionTimeout()))
                .build());
    }

    /**
     * Package-private constructor for testing with a custom HttpClient.
     */
    OpenRemoteRestClient(ConfigurationService configurationService, HttpClient httpClient) {
        this.configurationService = configurationService;
        this.objectMapper = new ObjectMapper();
        this.httpClient = httpClient;
    }

    /**
     * Authenticates with the Open Remote API using the provided credentials.
     *
     * @param username the authentication username
     * @param token    the authentication token (secret)
     * @return a CompletableFuture containing the authentication response
     */
    public CompletableFuture<AuthResponse> authenticate(String username, String token) {
        String baseUrl = configurationService.getApiEndpoint();
        String url = baseUrl + "/api/auth/login";

        log.debug("Authenticating with Open Remote API: POST {}", url);

        try {
            String requestBody = objectMapper.writeValueAsString(
                    Map.of("username", username, "secret", token)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofMillis(configurationService.getConnectionTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> handleAuthResponse(response, url));
        } catch (Exception e) {
            log.error("[{}] Authentication request failed - URL: {}, Error: {}",
                    Instant.now(), url, e.getMessage(), e);
            return CompletableFuture.failedFuture(
                    new RuntimeException("Failed to create authentication request: " + e.getMessage(), e));
        }
    }

    /**
     * Retrieves all available realms from the Open Remote API.
     *
     * @param sessionToken the session token obtained from authentication
     * @return a CompletableFuture containing the list of realms
     */
    public CompletableFuture<List<RealmDTO>> getRealms(String sessionToken) {
        String baseUrl = configurationService.getApiEndpoint();
        String url = baseUrl + "/api/" + sessionToken + "/realm/all";

        log.debug("Fetching realms from Open Remote API: GET {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofMillis(configurationService.getConnectionTimeout()))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> handleRealmsResponse(response, url));
    }

    /**
     * Retrieves all vehicles for a specific realm from the Open Remote API.
     *
     * @param realmId      the ID of the realm to fetch vehicles for
     * @param sessionToken the session token obtained from authentication
     * @return a CompletableFuture containing the list of vehicles
     */
    public CompletableFuture<List<VehicleDTO>> getVehicles(String realmId, String sessionToken) {
        String baseUrl = configurationService.getApiEndpoint();
        String url = baseUrl + "/api/" + sessionToken + "/realm/" + realmId + "/asset?type=vehicle";

        log.debug("Fetching vehicles for realm '{}' from Open Remote API: GET {}", realmId, url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofMillis(configurationService.getConnectionTimeout()))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> handleVehiclesResponse(response, realmId, url));
    }

    private AuthResponse handleAuthResponse(HttpResponse<String> response, String url) {
        int statusCode = response.statusCode();

        if (statusCode >= 200 && statusCode < 300) {
            try {
                AuthResponse authResponse = objectMapper.readValue(response.body(), AuthResponse.class);
                log.debug("Authentication successful");
                return authResponse;
            } catch (Exception e) {
                log.error("[{}] Failed to parse authentication response - URL: {}, Body: {}, Error: {}",
                        Instant.now(), url, response.body(), e.getMessage(), e);
                throw new RuntimeException("Failed to parse authentication response: " + e.getMessage(), e);
            }
        } else {
            String errorMsg = String.format(
                    "Authentication failed - URL: %s, Status: %d, Body: %s",
                    url, statusCode, response.body());
            log.error("[{}] {}", Instant.now(), errorMsg);
            throw new RuntimeException(errorMsg);
        }
    }

    private List<RealmDTO> handleRealmsResponse(HttpResponse<String> response, String url) {
        int statusCode = response.statusCode();

        if (statusCode >= 200 && statusCode < 300) {
            try {
                List<RealmDTO> realms = objectMapper.readValue(
                        response.body(), new TypeReference<List<RealmDTO>>() {});
                log.debug("Successfully retrieved {} realms", realms.size());
                return realms;
            } catch (Exception e) {
                log.error("[{}] Failed to parse realms response - URL: {}, Body: {}, Error: {}",
                        Instant.now(), url, response.body(), e.getMessage(), e);
                throw new RuntimeException("Failed to parse realms response: " + e.getMessage(), e);
            }
        } else {
            String errorMsg = String.format(
                    "Realm retrieval failed - URL: %s, Status: %d, Body: %s",
                    url, statusCode, response.body());
            log.error("[{}] {}", Instant.now(), errorMsg);
            throw new RuntimeException(errorMsg);
        }
    }

    private List<VehicleDTO> handleVehiclesResponse(HttpResponse<String> response, String realmId, String url) {
        int statusCode = response.statusCode();

        if (statusCode >= 200 && statusCode < 300) {
            try {
                List<VehicleDTO> vehicles = objectMapper.readValue(
                        response.body(), new TypeReference<List<VehicleDTO>>() {});
                log.debug("Successfully retrieved {} vehicles for realm '{}'", vehicles.size(), realmId);
                return vehicles;
            } catch (Exception e) {
                log.error("[{}] Failed to parse vehicles response - URL: {}, Realm: {}, Body: {}, Error: {}",
                        Instant.now(), url, realmId, response.body(), e.getMessage(), e);
                throw new RuntimeException("Failed to parse vehicles response: " + e.getMessage(), e);
            }
        } else {
            String errorMsg = String.format(
                    "Vehicle retrieval failed - URL: %s, Realm: %s, Status: %d, Body: %s",
                    url, realmId, statusCode, response.body());
            log.error("[{}] {}", Instant.now(), errorMsg);
            throw new RuntimeException(errorMsg);
        }
    }
}
