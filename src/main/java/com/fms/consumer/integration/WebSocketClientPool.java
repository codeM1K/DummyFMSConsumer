package com.fms.consumer.integration;

import com.fms.consumer.model.Vehicle;
import com.fms.consumer.service.AuthenticationService;
import com.fms.consumer.service.ConfigurationService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages a pool of WebSocket connections for vehicle location data consumption.
 * Uses ConcurrentHashMap for thread-safe connection tracking and an ExecutorService
 * for asynchronous connection management.
 *
 * Connection keys follow the pattern "{vehicleId}_{clientId}" to support multi-client mode.
 */
@Component
public class WebSocketClientPool {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketClientPool.class);

    private final ConcurrentHashMap<String, WebSocketConnection> connections;
    private final ExecutorService executor;
    private final LocationDataHandler dataHandler;
    private final ConfigurationService configService;
    private final AuthenticationService authenticationService;

    @Autowired
    public WebSocketClientPool(LocationDataHandler dataHandler, ConfigurationService configService,
                               AuthenticationService authenticationService) {
        this.connections = new ConcurrentHashMap<>();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ws-pool-" + Thread.currentThread().getId());
            t.setDaemon(true);
            return t;
        });
        this.dataHandler = dataHandler;
        this.configService = configService;
        this.authenticationService = authenticationService;
        logger.info("WebSocketClientPool initialized");
    }

    /**
     * Package-private constructor for testing without AuthenticationService.
     */
    WebSocketClientPool(LocationDataHandler dataHandler, ConfigurationService configService) {
        this(dataHandler, configService, null);
    }

    /**
     * Creates a new WebSocket connection for the given vehicle and client.
     * The connection is established asynchronously and stored in the pool upon success.
     * The endpoint URL is: wss://{host}/api/master/event/ws?token=ACCESS_TOKEN
     *
     * @param vehicle  the vehicle to connect for
     * @param clientId the client identifier for multi-client support
     * @return CompletableFuture that completes with the connection when established
     */
    public CompletableFuture<WebSocketConnection> createConnection(Vehicle vehicle, String clientId) {
        String connectionKey = buildConnectionKey(vehicle.getId(), clientId);
        String endpointUrl = buildWebSocketEndpointUrl();

        logger.info("Creating WebSocket connection for vehicle {} (client: {}), key: {}",
                vehicle.getId(), clientId, connectionKey);

        WebSocketConnection connection = new WebSocketConnection(
                vehicle, clientId, dataHandler, configService, endpointUrl);

        connections.put(connectionKey, connection);

        return CompletableFuture.supplyAsync(() -> {
            connection.connect().thenRun(() -> {
                connection.subscribe();
                logger.info("WebSocket connection established and subscribed for key: {}", connectionKey);
            }).exceptionally(ex -> {
                logger.warn("Failed to establish WebSocket connection for key {}: {}",
                        connectionKey, ex.getMessage());
                return null;
            });
            return connection;
        }, executor);
    }

    /**
     * Closes a specific connection identified by vehicle ID and client ID.
     * Removes the connection from the pool after closing.
     *
     * @param vehicleId the vehicle identifier
     * @param clientId  the client identifier
     */
    public void closeConnection(String vehicleId, String clientId) {
        String connectionKey = buildConnectionKey(vehicleId, clientId);
        WebSocketConnection connection = connections.remove(connectionKey);
        if (connection != null) {
            logger.info("Closing WebSocket connection for key: {}", connectionKey);
            connection.close();
        } else {
            logger.warn("No connection found for key: {}", connectionKey);
        }
    }

    /**
     * Closes ALL connections for a specific vehicle (across all clients).
     * Iterates through all connections, closing those that match the vehicle ID.
     *
     * @param vehicleId the vehicle identifier
     */
    public void closeConnection(String vehicleId) {
        String prefix = vehicleId + "_";
        List<String> keysToRemove = new ArrayList<>();

        for (Map.Entry<String, WebSocketConnection> entry : connections.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                keysToRemove.add(entry.getKey());
            }
        }

        for (String key : keysToRemove) {
            WebSocketConnection connection = connections.remove(key);
            if (connection != null) {
                logger.info("Closing WebSocket connection for key: {}", key);
                connection.close();
            }
        }

        if (keysToRemove.isEmpty()) {
            logger.warn("No connections found for vehicleId: {}", vehicleId);
        } else {
            logger.info("Closed {} connection(s) for vehicleId: {}", keysToRemove.size(), vehicleId);
        }
    }

    /**
     * Closes all connections in the pool and clears the map.
     * Used for shutdown or mode transitions.
     */
    public void closeAllConnections() {
        logger.info("Closing all WebSocket connections. Current count: {}", connections.size());

        for (Map.Entry<String, WebSocketConnection> entry : connections.entrySet()) {
            try {
                entry.getValue().close();
                logger.debug("Closed connection: {}", entry.getKey());
            } catch (Exception e) {
                logger.warn("Error closing connection {}: {}", entry.getKey(), e.getMessage());
            }
        }

        connections.clear();
        logger.info("All WebSocket connections closed and pool cleared");
    }

    /**
     * Returns the number of currently active (connected) connections in the pool.
     *
     * @return count of connected connections
     */
    public int getActiveConnectionCount() {
        return (int) connections.values().stream()
                .filter(WebSocketConnection::isConnected)
                .count();
    }

    /**
     * Returns a specific connection from the pool, or null if not found.
     *
     * @param vehicleId the vehicle identifier
     * @param clientId  the client identifier
     * @return the connection, or null if not present
     */
    public WebSocketConnection getConnection(String vehicleId, String clientId) {
        return connections.get(buildConnectionKey(vehicleId, clientId));
    }

    /**
     * Returns the total number of connections in the pool (regardless of connected state).
     *
     * @return total connection count
     */
    public int getTotalConnectionCount() {
        return connections.size();
    }

    /**
     * Cleans up the executor and closes all connections on application shutdown.
     */
    @PreDestroy
    public void shutdown() {
        logger.info("WebSocketClientPool shutting down");
        closeAllConnections();

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                logger.warn("Executor did not terminate gracefully, forced shutdown");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warn("Executor shutdown interrupted");
        }

        logger.info("WebSocketClientPool shutdown complete");
    }

    /**
     * Builds a connection key from vehicle ID and client ID.
     *
     * @param vehicleId the vehicle identifier
     * @param clientId  the client identifier
     * @return the connection key in format "{vehicleId}_{clientId}"
     */
    private String buildConnectionKey(String vehicleId, String clientId) {
        return vehicleId + "_" + clientId;
    }

    /**
     * Builds the WebSocket endpoint URL using the configured API base URL and access token.
     * Converts https:// to wss:// and http:// to ws://.
     * Path: /api/master/event/ws?token=ACCESS_TOKEN
     *
     * @return the WebSocket endpoint URL
     */
    private String buildWebSocketEndpointUrl() {
        String baseUrl = configService.getApiEndpoint();
        String wsBaseUrl = baseUrl.replace("https://", "wss://").replace("http://", "ws://");
        String token = (authenticationService != null) ? authenticationService.getSessionToken() : null;
        return wsBaseUrl + "/api/master/event/ws" + (token != null ? "?token=" + token : "");
    }
}
