package com.fms.consumer.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fms.consumer.integration.dto.AuthResponse;
import com.fms.consumer.integration.dto.RealmDTO;
import com.fms.consumer.integration.dto.VehicleDTO;
import com.fms.consumer.service.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST client for communicating with the Open Remote API.
 * Handles authentication via OAuth2 Client Credentials (Keycloak),
 * realm discovery, and vehicle (asset) querying.
 */
@Component
public class OpenRemoteRestClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRemoteRestClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ConfigurationService configurationService;

    @Autowired
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
     * Authenticates using OAuth2 Client Credentials flow against Keycloak.
     * POST to /auth/realms/master/protocol/openid-connect/token
     * with form-urlencoded body: grant_type, client_id, client_secret.
     *
     * @param clientId     the OAuth2 client ID
     * @param clientSecret the OAuth2 client secret
     * @return a CompletableFuture containing the authentication response
     */
    public CompletableFuture<AuthResponse> authenticate(String clientId, String clientSecret) {
        String baseUrl = configurationService.getApiEndpoint();
        String url = baseUrl + "/auth/realms/master/protocol/openid-connect/token";

        log.debug("Authenticating with Open Remote via OAuth2 Client Credentials: POST {}", url);

        String formBody = "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofMillis(configurationService.getConnectionTimeout()))
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> handleAuthResponse(response, url));
    }

    /**
     * Retrieves all accessible realms.
     * GET /api/master/realm with Bearer token.
     *
     * @param accessToken the OAuth2 access token obtained from authentication
     * @return a CompletableFuture containing the list of realms
     */
    public CompletableFuture<List<RealmDTO>> getRealms(String accessToken) {
        String baseUrl = configurationService.getApiEndpoint();
        String url = baseUrl + "/api/master/realm";

        log.debug("Fetching realms: GET {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .timeout(Duration.ofMillis(configurationService.getConnectionTimeout()))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> handleRealmsResponse(response, url));
    }

    /**
     * Queries assets (vehicles) for a specific realm.
     * POST /api/master/asset/query with Bearer token and JSON body.
     *
     * @param realmId     the realm name to query assets for
     * @param accessToken the OAuth2 access token obtained from authentication
     * @return a CompletableFuture containing the list of vehicles
     */
    public CompletableFuture<List<VehicleDTO>> getVehicles(String realmId, String accessToken) {
        String baseUrl = configurationService.getApiEndpoint();
        String url = baseUrl + "/api/master/asset/query";

        log.debug("Querying vehicles for realm '{}': POST {}", realmId, url);

        try {
            String requestBody = objectMapper.writeValueAsString(
                    Map.of(
                        "realm", Map.of("name", realmId),
                        "select", List.of("id", "name", "type", "attributes", "path")
                    )
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofMillis(configurationService.getConnectionTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> handleVehiclesResponse(response, realmId, url));
        } catch (Exception e) {
            log.error("[{}] Failed to create vehicle query request: {}", Instant.now(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private AuthResponse handleAuthResponse(HttpResponse<String> response, String url) {
        int statusCode = response.statusCode();

        if (statusCode >= 200 && statusCode < 300) {
            try {
                JsonNode root = objectMapper.readTree(response.body());
                String accessToken = root.get("access_token").asText();
                int expiresIn = root.has("expires_in") ? root.get("expires_in").asInt(300) : 300;
                AuthResponse authResponse = new AuthResponse(accessToken, true, null);
                authResponse.setExpiresIn(expiresIn);
                log.debug("OAuth2 authentication successful, token expires in {}s", expiresIn);
                return authResponse;
            } catch (Exception e) {
                log.error("[{}] Failed to parse OAuth2 token response - URL: {}, Error: {}",
                        Instant.now(), url, e.getMessage(), e);
                throw new RuntimeException("Failed to parse auth response: " + e.getMessage(), e);
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
