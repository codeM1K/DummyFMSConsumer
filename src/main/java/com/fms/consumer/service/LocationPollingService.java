package com.fms.consumer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fms.consumer.integration.LocationDataHandler;
import com.fms.consumer.model.LocationData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service that polls the Open Remote REST API for location data updates.
 * Replaces WebSocket-based location streaming since the WebSocket endpoint
 * at fms.pcp.com.gr is not available (returns 404).
 *
 * <p>Polls the asset query endpoint every 1 second to get updated location
 * data from the {@code attributes.location} field of each asset. Only polls
 * for subscribed vehicles when consumption is active.</p>
 */
@Service
public class LocationPollingService {

    private static final Logger log = LoggerFactory.getLogger(LocationPollingService.class);

    private final ConfigurationService configService;
    private final AuthenticationService authService;
    private final LocationDataHandler locationDataHandler;
    private final MetricsCollector metricsCollector;
    private final ScheduledExecutorService scheduler;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile boolean active = false;
    private final Set<String> subscribedVehicleIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, String> vehicleClientMap = new ConcurrentHashMap<>();

    @Autowired
    public LocationPollingService(ConfigurationService configService,
                                  AuthenticationService authService,
                                  LocationDataHandler locationDataHandler,
                                  MetricsCollector metricsCollector) {
        this.configService = configService;
        this.authService = authService;
        this.locationDataHandler = locationDataHandler;
        this.metricsCollector = metricsCollector;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(configService.getConnectionTimeout()))
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "location-poller");
            t.setDaemon(true);
            return t;
        });
        // Start polling every 1 second
        scheduler.scheduleAtFixedRate(this::pollLocationData, 1, 1, TimeUnit.SECONDS);
        log.info("LocationPollingService initialized with 1-second polling interval");
    }

    /**
     * Activates the polling service. When active, the service will poll for
     * location data of subscribed vehicles.
     */
    public void start() {
        if (!this.active) {
            this.active = true;
            log.info("LocationPollingService started");
        }
    }

    /**
     * Deactivates the polling service. Polling loop will skip execution
     * while inactive.
     */
    public void stop() {
        this.active = false;
        log.info("LocationPollingService stopped");
    }

    /**
     * Returns whether the polling service is currently active.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Subscribes a vehicle for location data polling.
     *
     * @param vehicleId the vehicle ID to subscribe
     * @param clientId  the client ID that initiated the subscription
     */
    public void subscribeVehicle(String vehicleId, String clientId) {
        subscribedVehicleIds.add(vehicleId);
        vehicleClientMap.put(vehicleId, clientId);
        log.debug("Subscribed vehicle '{}' for location polling (client: '{}')", vehicleId, clientId);
    }

    /**
     * Unsubscribes a vehicle from location data polling.
     *
     * @param vehicleId the vehicle ID to unsubscribe
     */
    public void unsubscribeVehicle(String vehicleId) {
        subscribedVehicleIds.remove(vehicleId);
        vehicleClientMap.remove(vehicleId);
        log.debug("Unsubscribed vehicle '{}' from location polling", vehicleId);
    }

    /**
     * Unsubscribes all vehicles from location data polling.
     */
    public void unsubscribeAll() {
        subscribedVehicleIds.clear();
        vehicleClientMap.clear();
        log.debug("Unsubscribed all vehicles from location polling");
    }

    /**
     * Returns the set of currently subscribed vehicle IDs.
     */
    public Set<String> getSubscribedVehicleIds() {
        return Collections.unmodifiableSet(subscribedVehicleIds);
    }

    /**
     * Polls the Open Remote asset query endpoint for location data.
     * Only executes when active and there are subscribed vehicles.
     */
    private void pollLocationData() {
        if (!active || subscribedVehicleIds.isEmpty()) {
            return;
        }

        String token = authService.getSessionToken();
        if (token == null) {
            log.debug("No auth token available, skipping location poll");
            return;
        }

        try {
            String baseUrl = configService.getApiEndpoint();
            String url = baseUrl + "/api/master/asset/query";
            String requestBody = objectMapper.writeValueAsString(
                    Map.of("select", Map.of("include", "ALL"))
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(configService.getConnectionTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            parseAndNotifyLocations(response.body());
                        } else {
                            log.debug("Location poll returned status {}", response.statusCode());
                        }
                    })
                    .exceptionally(ex -> {
                        log.debug("Location poll failed: {}", ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.debug("Error during location poll: {}", e.getMessage());
        }
    }

    /**
     * Parses the asset query response and notifies listeners for each subscribed
     * vehicle that has location data available.
     *
     * <p>The asset response format:
     * <pre>
     * [
     *   {
     *     "id": "...",
     *     "name": "...",
     *     "realm": "master",
     *     "type": "GroupAsset",
     *     "attributes": {
     *       "location": {
     *         "value": {"type": "Point", "coordinates": [lon, lat]},
     *         "timestamp": 1234567890000
     *       }
     *     }
     *   }
     * ]
     * </pre>
     *
     * @param responseBody the JSON response body from the asset query endpoint
     */
    private void parseAndNotifyLocations(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (!root.isArray()) return;

            for (JsonNode asset : root) {
                String assetId = asset.has("id") ? asset.get("id").asText() : null;
                if (assetId == null || !subscribedVehicleIds.contains(assetId)) continue;

                String name = asset.has("name") ? asset.get("name").asText() : "Unknown";
                String realm = asset.has("realm") ? asset.get("realm").asText() : "";

                // Extract location from attributes.location.value
                JsonNode attributes = asset.get("attributes");
                if (attributes == null) continue;

                JsonNode locationAttr = attributes.get("location");
                if (locationAttr == null) continue;

                JsonNode locationValue = locationAttr.get("value");
                if (locationValue == null || locationValue.isNull()) continue;

                // GeoJSON Point: {"type":"Point","coordinates":[lon, lat]}
                double latitude = 0;
                double longitude = 0;
                long timestamp = locationAttr.has("timestamp")
                        ? locationAttr.get("timestamp").asLong()
                        : System.currentTimeMillis();

                if (locationValue.has("coordinates") && locationValue.get("coordinates").isArray()) {
                    JsonNode coords = locationValue.get("coordinates");
                    if (coords.size() >= 2) {
                        longitude = coords.get(0).asDouble();
                        latitude = coords.get(1).asDouble();
                    }
                }

                // Skip assets with no actual location (0,0)
                if (latitude == 0 && longitude == 0) continue;

                // Build metadata
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("vehicleName", name);
                metadata.put("realmId", realm);
                metadata.put("assetType", asset.has("type") ? asset.get("type").asText() : "");

                // Create LocationData and notify listeners
                LocationData locationData = new LocationData(
                        assetId, latitude, longitude,
                        Instant.ofEpochMilli(timestamp), metadata);
                locationDataHandler.notifyListeners(locationData);

                // Record metrics
                String clientId = vehicleClientMap.getOrDefault(assetId, "client_1");
                metricsCollector.recordLocationUpdate(assetId, clientId);
            }
        } catch (Exception e) {
            log.warn("Error parsing location poll response: {}", e.getMessage());
        }
    }

    /**
     * Shuts down the polling scheduler on application destruction.
     */
    @PreDestroy
    public void shutdown() {
        active = false;
        scheduler.shutdownNow();
        log.info("LocationPollingService shut down");
    }
}
