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
import java.util.Random;

/**
 * Service that polls the Open Remote REST API for location data updates.
 * Replaces WebSocket-based location streaming since the WebSocket endpoint
 * at fms.pcp.com.gr is not available (returns 404).
 *
 * <p>Polls the asset query endpoint at a configurable interval (minimum 2 seconds)
 * to get updated location data from the {@code attributes.location} field of each
 * asset. Only polls for subscribed vehicles when consumption is active.</p>
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
    private volatile boolean adaptiveThrottlingEnabled = false;
    private volatile int currentPollIntervalSeconds;
    private final int basePollIntervalSeconds;
    private volatile int consecutiveFastResponses = 0;
    private volatile long lastResponseTimeMs = 0;
    private volatile ScheduledFuture<?> pollTask;

    // Random mode fields
    private volatile boolean randomMode = false;
    private final Random random = new Random();
    private volatile int currentRandomClientCount = 5;
    private volatile int lastRandomDelaySeconds = 2;

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
        // Start polling with configurable interval (minimum 2 seconds)
        this.basePollIntervalSeconds = Math.max(2, configService.getMetricsRefreshInterval());
        this.currentPollIntervalSeconds = basePollIntervalSeconds;
        this.pollTask = scheduler.scheduleAtFixedRate(this::pollLocationData, basePollIntervalSeconds, basePollIntervalSeconds, TimeUnit.SECONDS);
        log.info("LocationPollingService initialized with {}-second polling interval", basePollIntervalSeconds);
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
     * Measures response time and applies adaptive throttling if enabled.
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

        long startTime = System.currentTimeMillis();

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
                        long responseTime = System.currentTimeMillis() - startTime;
                        lastResponseTimeMs = responseTime;

                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            parseAndNotifyLocations(response.body());
                        } else {
                            log.debug("Location poll returned status {}", response.statusCode());
                        }

                        if (adaptiveThrottlingEnabled) {
                            applyAdaptiveThrottling(responseTime);
                        }
                    })
                    .exceptionally(ex -> {
                        long responseTime = System.currentTimeMillis() - startTime;
                        lastResponseTimeMs = responseTime;

                        if (adaptiveThrottlingEnabled) {
                            applyAdaptiveThrottling(responseTime > 0 ? responseTime : 10000);
                        }

                        log.debug("Location poll failed: {}", ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.debug("Error during location poll: {}", e.getMessage());
        }
    }

    /**
     * Enables or disables adaptive throttling.
     * When disabled, resets the poll interval back to the base interval.
     *
     * @param enabled true to enable adaptive throttling
     */
    public void setAdaptiveThrottlingEnabled(boolean enabled) {
        this.adaptiveThrottlingEnabled = enabled;
        if (!enabled) {
            adjustPollInterval(basePollIntervalSeconds);
        }
        log.info("Adaptive throttling {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Returns whether adaptive throttling is currently enabled.
     */
    public boolean isAdaptiveThrottlingEnabled() {
        return adaptiveThrottlingEnabled;
    }

    /**
     * Enables or disables random mode polling.
     * When enabled: cancels the fixed scheduler and starts random scheduling (2-10s intervals with bursts).
     * When disabled: cancels random scheduling and restarts the fixed scheduler.
     *
     * @param enabled true to enable random mode polling
     */
    public void setRandomMode(boolean enabled) {
        this.randomMode = enabled;
        if (enabled) {
            // Cancel fixed scheduler, start random scheduling
            if (pollTask != null) {
                pollTask.cancel(false);
                pollTask = null;
            }
            scheduleNextRandomPoll();
        } else {
            // Restore fixed interval scheduling
            if (pollTask != null) {
                pollTask.cancel(false);
            }
            pollTask = scheduler.scheduleAtFixedRate(this::pollLocationData, currentPollIntervalSeconds, currentPollIntervalSeconds, TimeUnit.SECONDS);
        }
        log.info("Random mode polling {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Returns whether random mode polling is currently enabled.
     */
    public boolean isRandomMode() {
        return randomMode;
    }

    /**
     * Returns the current random client count (number of simulated clients per poll cycle).
     */
    public int getCurrentRandomClientCount() {
        return currentRandomClientCount;
    }

    /**
     * Returns the last random delay in seconds used for scheduling.
     */
    public int getLastRandomDelaySeconds() {
        return lastRandomDelaySeconds;
    }

    /**
     * Schedules the next random poll with a randomized delay (2-10 seconds).
     * Has a 20% chance of producing a burst (3 rapid polls in 1-second intervals).
     */
    private void scheduleNextRandomPoll() {
        if (!active || !randomMode) return;

        // Randomize next poll delay: 2-10 seconds
        int nextDelay = 2 + random.nextInt(9); // 2 to 10 seconds
        lastRandomDelaySeconds = nextDelay;

        // Sometimes burst: 20% chance of a rapid burst (3 polls in 1 second intervals)
        boolean burst = random.nextInt(5) == 0;

        if (burst) {
            log.debug("Random mode: scheduling burst (3 rapid polls)");
            // Schedule 3 rapid polls
            for (int i = 0; i < 3; i++) {
                scheduler.schedule(this::executeRandomPoll, i, TimeUnit.SECONDS);
            }
            // Then schedule next random poll after the burst
            scheduler.schedule(this::scheduleNextRandomPoll, 4, TimeUnit.SECONDS);
        } else {
            // Normal: schedule one poll then the next random interval
            scheduler.schedule(() -> {
                executeRandomPoll();
                scheduleNextRandomPoll();
            }, nextDelay, TimeUnit.SECONDS);
        }
    }

    /**
     * Executes a single random poll: randomizes the client count (5-100),
     * performs the actual poll, and records simulated multi-client metrics.
     */
    private void executeRandomPoll() {
        if (!active || !randomMode) return;

        // Randomize client count: 5 to 100
        currentRandomClientCount = 5 + random.nextInt(96); // 5 to 100

        // Do the actual poll
        pollLocationData();

        // Record metrics for each simulated client beyond the first
        for (int i = 2; i <= currentRandomClientCount; i++) {
            String clientId = "client_" + i;
            for (String vehicleId : subscribedVehicleIds) {
                metricsCollector.recordLocationUpdate(vehicleId, clientId);
            }
        }
    }

    /**
     * Returns the current poll interval in seconds.
     */
    public int getCurrentPollIntervalSeconds() {
        return currentPollIntervalSeconds;
    }

    /**
     * Returns the last measured response time in milliseconds.
     */
    public long getLastResponseTimeMs() {
        return lastResponseTimeMs;
    }

    /**
     * Adjusts the polling interval by cancelling the current schedule
     * and rescheduling with the new interval.
     *
     * @param newIntervalSeconds the new polling interval in seconds
     */
    private void adjustPollInterval(int newIntervalSeconds) {
        if (newIntervalSeconds == currentPollIntervalSeconds) return;

        int oldInterval = currentPollIntervalSeconds;
        currentPollIntervalSeconds = newIntervalSeconds;

        if (pollTask != null) {
            pollTask.cancel(false);
        }
        pollTask = scheduler.scheduleAtFixedRate(this::pollLocationData, newIntervalSeconds, newIntervalSeconds, TimeUnit.SECONDS);
        log.info("[{}] Adaptive throttle: poll interval changed from {}s to {}s (last response: {}ms)",
                Instant.now(), oldInterval, newIntervalSeconds, lastResponseTimeMs);
    }

    /**
     * Applies adaptive throttling logic based on the measured response time.
     * Increases interval for slow responses and decreases for consistently fast ones.
     *
     * @param responseTimeMs the response time in milliseconds
     */
    private void applyAdaptiveThrottling(long responseTimeMs) {
        if (responseTimeMs > 8000) {
            consecutiveFastResponses = 0;
            adjustPollInterval(20);
        } else if (responseTimeMs > 4000) {
            consecutiveFastResponses = 0;
            adjustPollInterval(10);
        } else if (responseTimeMs > 2000) {
            consecutiveFastResponses = 0;
            adjustPollInterval(5);
        } else if (responseTimeMs < 1000) {
            consecutiveFastResponses++;
            if (consecutiveFastResponses >= 5 && currentPollIntervalSeconds > basePollIntervalSeconds) {
                int newInterval = Math.max(basePollIntervalSeconds, currentPollIntervalSeconds / 2);
                consecutiveFastResponses = 0;
                adjustPollInterval(newInterval);
            }
        } else {
            // Between 1-2 seconds - stay at current interval
            consecutiveFastResponses = 0;
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
