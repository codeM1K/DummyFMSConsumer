package com.fms.consumer.integration;

import com.fms.consumer.model.LocationData;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.service.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Represents a single WebSocket connection to receive location data for a vehicle.
 * Uses Java's built-in {@link java.net.http.WebSocket} (Java 11+) for WebSocket communication.
 * Implements automatic reconnection with exponential backoff on connection failures.
 *
 * Thread-safe: uses volatile flags for connection state.
 */
public class WebSocketConnection implements WebSocket.Listener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConnection.class);

    private final Vehicle vehicle;
    private final String clientId;
    private final LocationDataHandler dataHandler;
    private final ConfigurationService configService;
    private final String endpointUrl;
    private final ScheduledExecutorService scheduler;

    private volatile WebSocket webSocket;
    private volatile boolean connected;
    private volatile boolean active;

    private final StringBuilder messageBuffer = new StringBuilder();
    private int reconnectAttempt = 0;

    /**
     * Creates a new WebSocket connection for a specific vehicle.
     *
     * @param vehicle       the vehicle to subscribe to
     * @param clientId      the client identifier for this connection
     * @param dataHandler   handler for parsing and dispatching location data
     * @param configService configuration service for retry parameters
     * @param endpointUrl   the WebSocket endpoint URL
     */
    public WebSocketConnection(Vehicle vehicle, String clientId, LocationDataHandler dataHandler,
                               ConfigurationService configService, String endpointUrl) {
        this.vehicle = vehicle;
        this.clientId = clientId;
        this.dataHandler = dataHandler;
        this.configService = configService;
        this.endpointUrl = endpointUrl;
        this.connected = false;
        this.active = false;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ws-reconnect-" + vehicle.getId() + "-" + clientId);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Establishes the WebSocket connection to the endpoint.
     * Sets the connection to active and attempts to connect.
     *
     * @return CompletableFuture that completes when connection is established
     */
    public CompletableFuture<Void> connect() {
        this.active = true;
        return doConnect();
    }

    private CompletableFuture<Void> doConnect() {
        logger.info("Connecting WebSocket for vehicle {} (client: {})", vehicle.getId(), clientId);
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            URI uri = URI.create(endpointUrl);

            return httpClient.newWebSocketBuilder()
                    .buildAsync(uri, this)
                    .thenAccept(ws -> {
                        this.webSocket = ws;
                        this.connected = true;
                        this.reconnectAttempt = 0;
                        logger.info("WebSocket connected for vehicle {} (client: {})", vehicle.getId(), clientId);
                    })
                    .exceptionally(ex -> {
                        logger.warn("Failed to connect WebSocket for vehicle {} (client: {}): {}",
                                vehicle.getId(), clientId, ex.getMessage());
                        this.connected = false;
                        if (active) {
                            scheduleReconnect();
                        }
                        return null;
                    });
        } catch (Exception e) {
            logger.warn("Error initiating WebSocket connection for vehicle {} (client: {}): {}",
                    vehicle.getId(), clientId, e.getMessage());
            this.connected = false;
            if (active) {
                scheduleReconnect();
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Sends a subscription message for the vehicle's location data updates.
     * Must be called after the connection is established.
     */
    public void subscribe() {
        if (!connected || webSocket == null) {
            logger.warn("Cannot subscribe for vehicle {} - not connected", vehicle.getId());
            return;
        }

        String subscriptionMessage = String.format(
                "{\"type\":\"subscribe\",\"vehicleId\":\"%s\"}", vehicle.getId());

        webSocket.sendText(subscriptionMessage, true)
                .thenRun(() -> logger.info("Subscribed to location data for vehicle {} (client: {})",
                        vehicle.getId(), clientId))
                .exceptionally(ex -> {
                    logger.warn("Failed to send subscription for vehicle {} (client: {}): {}",
                            vehicle.getId(), clientId, ex.getMessage());
                    return null;
                });
    }

    /**
     * Gracefully closes the WebSocket connection.
     * Sets active to false to prevent reconnection attempts.
     */
    public void close() {
        logger.info("Closing WebSocket connection for vehicle {} (client: {})", vehicle.getId(), clientId);
        this.active = false;
        this.connected = false;

        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client closing")
                        .thenRun(() -> logger.info("WebSocket closed for vehicle {} (client: {})",
                                vehicle.getId(), clientId))
                        .exceptionally(ex -> {
                            logger.warn("Error closing WebSocket for vehicle {} (client: {}): {}",
                                    vehicle.getId(), clientId, ex.getMessage());
                            return null;
                        });
            } catch (Exception e) {
                logger.warn("Exception while closing WebSocket for vehicle {} (client: {}): {}",
                        vehicle.getId(), clientId, e.getMessage());
            }
        }

        scheduler.shutdown();
    }

    /**
     * Returns whether the connection is currently active and connected.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Returns whether this connection is still active (not closed).
     *
     * @return true if active, false if close() has been called
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Returns the vehicle associated with this connection.
     *
     * @return the vehicle
     */
    public Vehicle getVehicle() {
        return vehicle;
    }

    /**
     * Returns the client ID associated with this connection.
     *
     * @return the client ID
     */
    public String getClientId() {
        return clientId;
    }

    // --- WebSocket.Listener implementation ---

    @Override
    public void onOpen(WebSocket webSocket) {
        logger.info("WebSocket onOpen for vehicle {} (client: {})", vehicle.getId(), clientId);
        this.connected = true;
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messageBuffer.append(data);
        if (last) {
            String message = messageBuffer.toString();
            messageBuffer.setLength(0);
            handleLocationData(message);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        logger.info("[{}] WebSocket onClose for vehicle {} (client: {}): statusCode={}, reason={}",
                java.time.Instant.now(), vehicle.getId(), clientId, statusCode, reason);
        this.connected = false;

        if (active) {
            logger.warn("[{}] WebSocket connection dropped unexpectedly for vehicle {} (client: {}), scheduling reconnect",
                    java.time.Instant.now(), vehicle.getId(), clientId);
            scheduleReconnect();
        }
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        logger.error("[{}] WebSocket onError for vehicle {} (client: {}): {}",
                java.time.Instant.now(), vehicle.getId(), clientId, error.getMessage(), error);
        this.connected = false;

        if (active) {
            scheduleReconnect();
        }
    }

    @Override
    public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
        webSocket.request(1);
        return null;
    }

    // --- Private helper methods ---

    /**
     * Handles incoming location data by delegating to the LocationDataHandler.
     * Parses the message and notifies listeners if parsing succeeds.
     *
     * @param message the raw JSON message received from the WebSocket
     */
    private void handleLocationData(String message) {
        try {
            LocationData data = dataHandler.parse(message);
            if (data != null) {
                dataHandler.notifyListeners(data);
            }
        } catch (Exception e) {
            logger.error("[{}] Error handling location data for vehicle {} (client: {}): {}",
                    java.time.Instant.now(), vehicle.getId(), clientId, e.getMessage(), e);
        }
    }

    /**
     * Schedules a reconnection attempt using exponential backoff.
     * Delay starts at configService.getRetryInitialDelay(), doubles each attempt,
     * and is capped at configService.getRetryMaxDelay() (max 30 seconds).
     * Implements Req 12.2: exponential backoff up to 30s for WebSocket reconnection.
     */
    private void scheduleReconnect() {
        if (!active) {
            return;
        }

        long initialDelay = configService.getRetryInitialDelay();
        long maxDelay = configService.getRetryMaxDelay();
        long delay = Math.min(initialDelay * (1L << reconnectAttempt), maxDelay);
        reconnectAttempt++;

        logger.warn("[{}] Scheduling reconnect for vehicle {} (client: {}) in {}ms (attempt {}, maxDelay={}ms)",
                java.time.Instant.now(), vehicle.getId(), clientId, delay, reconnectAttempt, maxDelay);

        try {
            scheduler.schedule(() -> {
                if (active) {
                    logger.info("[{}] Attempting reconnect for vehicle {} (client: {}), attempt {}",
                            java.time.Instant.now(), vehicle.getId(), clientId, reconnectAttempt);
                    doConnect();
                }
            }, delay, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.error("[{}] Failed to schedule reconnect for vehicle {} (client: {}): {}",
                    java.time.Instant.now(), vehicle.getId(), clientId, e.getMessage(), e);
        }
    }
}
