package com.fms.consumer.service;

import com.fms.consumer.integration.WebSocketClientPool;
import com.fms.consumer.model.ConsumptionMode;
import com.fms.consumer.model.ConsumptionSession;
import com.fms.consumer.model.Realm;
import com.fms.consumer.model.Vehicle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Coordinates consumption activities across random and controlled modes.
 * Manages WebSocket connections for selected vehicles, supports multi-client
 * simulation, and integrates with MetricsCollector for metrics tracking.
 *
 * <p>Implements session resumption after network recovery (Req 12.3) and
 * isolated failure handling so one vehicle's failure does not affect others (Req 12.5).</p>
 */
@Service
public class ConsumptionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ConsumptionOrchestrator.class);

    private final WebSocketClientPool clientPool;
    private final MetricsCollector metricsCollector;
    private final DiscoveryService discoveryService;
    private final ConfigurationService configService;
    private final ScheduledExecutorService scheduler;

    private final ConcurrentHashMap<String, ConsumptionSession> activeSessions = new ConcurrentHashMap<>();
    /** Tracks sessions that were active before a network outage, for resumption purposes. */
    private final ConcurrentHashMap<String, ConsumptionSession> suspendedSessions = new ConcurrentHashMap<>();

    private volatile int clientCount = 1;
    private volatile boolean randomModeActive = false;
    private volatile ConsumptionMode currentMode = ConsumptionMode.IDLE;

    public ConsumptionOrchestrator(WebSocketClientPool clientPool,
                                   MetricsCollector metricsCollector,
                                   DiscoveryService discoveryService,
                                   ConfigurationService configService) {
        this.clientPool = clientPool;
        this.metricsCollector = metricsCollector;
        this.discoveryService = discoveryService;
        this.configService = configService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "orchestrator-scheduler");
            t.setDaemon(true);
            return t;
        });
        log.info("[{}] ConsumptionOrchestrator initialized", Instant.now());
    }

    /**
     * Starts Random Mode by selecting vehicles randomly from all available realms.
     * For each selected vehicle and each simulated client, creates a session and
     * establishes a WebSocket connection.
     * Each vehicle's connection is established independently; one vehicle's failure
     * does not affect others (Req 12.5).
     */
    public void startRandomMode() {
        log.info("[{}] Starting Random Mode with {} client(s)", Instant.now(), clientCount);

        List<Vehicle> allVehicles = getAllVehiclesFromRealms();
        if (allVehicles.isEmpty()) {
            log.warn("[{}] No vehicles available for Random Mode", Instant.now());
            return;
        }

        // Randomly select a subset of vehicles
        List<Vehicle> selectedVehicles = selectRandomVehicles(allVehicles);
        log.info("[{}] Randomly selected {} vehicles out of {} available",
                Instant.now(), selectedVehicles.size(), allVehicles.size());

        for (Vehicle vehicle : selectedVehicles) {
            for (int clientIdx = 1; clientIdx <= clientCount; clientIdx++) {
                String clientId = "client_" + clientIdx;
                startSessionForVehicle(vehicle, clientId, ConsumptionMode.RANDOM);
            }
        }

        randomModeActive = true;
        currentMode = ConsumptionMode.RANDOM;
        log.info("[{}] Random Mode started with {} active sessions", Instant.now(), activeSessions.size());
    }

    /**
     * Stops Random Mode by closing all sessions created in RANDOM mode.
     */
    public void stopRandomMode() {
        log.info("[{}] Stopping Random Mode", Instant.now());

        List<String> keysToRemove = activeSessions.entrySet().stream()
                .filter(entry -> entry.getValue().getMode() == ConsumptionMode.RANDOM)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (String key : keysToRemove) {
            ConsumptionSession session = activeSessions.remove(key);
            if (session != null) {
                session.deactivate();
                clientPool.closeConnection(session.getVehicle().getId(), session.getClientId());
                metricsCollector.recordConnectionChange(-1);
                metricsCollector.recordVehicleInactive(session.getVehicle().getId());
                if (session.getVehicle().getRealmId() != null) {
                    metricsCollector.recordRealmInactive(session.getVehicle().getRealmId());
                }
                log.debug("[{}] Closed session: {}", Instant.now(), key);
            }
        }

        randomModeActive = false;
        currentMode = ConsumptionMode.IDLE;
        log.info("[{}] Random Mode stopped. Removed {} sessions", Instant.now(), keysToRemove.size());
    }

    /**
     * Starts Controlled Mode for the specified set of user-selected vehicles.
     * For each vehicle and each simulated client, creates a session and
     * establishes a WebSocket connection.
     * Each vehicle's connection is established independently; one vehicle's failure
     * does not affect others (Req 12.5).
     *
     * @param vehicles the set of vehicles to consume data from
     */
    public void startControlledMode(Set<Vehicle> vehicles) {
        if (vehicles == null || vehicles.isEmpty()) {
            log.warn("[{}] No vehicles provided for Controlled Mode", Instant.now());
            return;
        }

        log.info("[{}] Starting Controlled Mode for {} vehicles with {} client(s)",
                Instant.now(), vehicles.size(), clientCount);

        for (Vehicle vehicle : vehicles) {
            for (int clientIdx = 1; clientIdx <= clientCount; clientIdx++) {
                String clientId = "client_" + clientIdx;
                startSessionForVehicle(vehicle, clientId, ConsumptionMode.CONTROLLED);
            }
        }

        currentMode = ConsumptionMode.CONTROLLED;
        log.info("[{}] Controlled Mode started. Total active sessions: {}",
                Instant.now(), activeSessions.size());
    }

    /**
     * Stops Controlled Mode for the specified set of vehicles.
     * Closes sessions only for the given vehicles. If no sessions remain,
     * transitions to IDLE mode.
     *
     * @param vehicles the set of vehicles to stop consuming
     */
    public void stopControlledMode(Set<Vehicle> vehicles) {
        if (vehicles == null || vehicles.isEmpty()) {
            log.warn("[{}] No vehicles provided for stopping Controlled Mode", Instant.now());
            return;
        }

        log.info("[{}] Stopping Controlled Mode for {} vehicles", Instant.now(), vehicles.size());

        Set<String> vehicleIds = vehicles.stream()
                .map(Vehicle::getId)
                .collect(Collectors.toSet());

        List<String> keysToRemove = activeSessions.entrySet().stream()
                .filter(entry -> entry.getValue().getMode() == ConsumptionMode.CONTROLLED)
                .filter(entry -> vehicleIds.contains(entry.getValue().getVehicle().getId()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (String key : keysToRemove) {
            ConsumptionSession session = activeSessions.remove(key);
            if (session != null) {
                session.deactivate();
                clientPool.closeConnection(session.getVehicle().getId(), session.getClientId());
                metricsCollector.recordConnectionChange(-1);
                metricsCollector.recordVehicleInactive(session.getVehicle().getId());
                if (session.getVehicle().getRealmId() != null) {
                    metricsCollector.recordRealmInactive(session.getVehicle().getRealmId());
                }
                log.debug("[{}] Closed controlled session: {}", Instant.now(), key);
            }
        }

        // If no sessions remain, transition to IDLE
        if (activeSessions.isEmpty()) {
            currentMode = ConsumptionMode.IDLE;
            log.info("[{}] No active sessions remain. Mode set to IDLE", Instant.now());
        }

        log.info("[{}] Stopped Controlled Mode for {} vehicles. Remaining sessions: {}",
                Instant.now(), vehicles.size(), activeSessions.size());
    }

    /**
     * Configures the number of simulated clients for multi-client mode.
     * Validates that the count is between 1 and the configured maximum.
     *
     * @param clientCount the desired number of simulated clients
     * @throws IllegalArgumentException if clientCount is out of valid range
     */
    public void configureMultiClient(int clientCount) {
        int maxClients = configService.getSimulationMaxClients();

        if (clientCount < 1) {
            throw new IllegalArgumentException(
                    "Client count must be at least 1, but was: " + clientCount);
        }
        if (clientCount > maxClients) {
            throw new IllegalArgumentException(
                    "Client count must not exceed " + maxClients + ", but was: " + clientCount);
        }

        int previousCount = this.clientCount;
        this.clientCount = clientCount;
        log.info("[{}] Multi-client configuration changed from {} to {} clients",
                Instant.now(), previousCount, clientCount);
    }

    /**
     * Resumes all previously active sessions after network recovery (Req 12.3).
     * When connectivity is restored, this method re-establishes WebSocket connections
     * for all sessions that were active before the outage. Each session is resumed
     * independently to ensure isolated failure handling (Req 12.5).
     */
    public void resumeAllSessions() {
        log.info("[{}] Resuming all active sessions after network recovery. Active sessions: {}, Suspended sessions: {}",
                Instant.now(), activeSessions.size(), suspendedSessions.size());

        // Move any suspended sessions back to active
        for (Map.Entry<String, ConsumptionSession> entry : suspendedSessions.entrySet()) {
            activeSessions.putIfAbsent(entry.getKey(), entry.getValue());
        }
        suspendedSessions.clear();

        // Re-establish connections for all active sessions independently
        int resumed = 0;
        int failed = 0;
        for (Map.Entry<String, ConsumptionSession> entry : activeSessions.entrySet()) {
            String sessionKey = entry.getKey();
            ConsumptionSession session = entry.getValue();
            try {
                resumeSession(session);
                resumed++;
                log.debug("[{}] Resumed session: {}", Instant.now(), sessionKey);
            } catch (Exception e) {
                // Isolated failure handling: log and continue with other vehicles (Req 12.5)
                failed++;
                log.error("[{}] Failed to resume session '{}' for vehicle '{}' (client: '{}'): {}",
                        Instant.now(), sessionKey, session.getVehicle().getId(),
                        session.getClientId(), e.getMessage(), e);
            }
        }

        log.info("[{}] Session resumption complete. Resumed: {}, Failed: {}, Total active: {}",
                Instant.now(), resumed, failed, activeSessions.size());
    }

    /**
     * Suspends all active sessions due to network outage.
     * Moves sessions to suspended state and closes their connections.
     * Sessions can be resumed later via {@link #resumeAllSessions()}.
     */
    public void suspendAllSessions() {
        log.warn("[{}] Suspending all active sessions due to network outage. Session count: {}",
                Instant.now(), activeSessions.size());

        for (Map.Entry<String, ConsumptionSession> entry : activeSessions.entrySet()) {
            suspendedSessions.put(entry.getKey(), entry.getValue());
            try {
                clientPool.closeConnection(
                        entry.getValue().getVehicle().getId(),
                        entry.getValue().getClientId());
            } catch (Exception e) {
                log.warn("[{}] Error closing connection during suspension for session '{}': {}",
                        Instant.now(), entry.getKey(), e.getMessage());
            }
        }

        log.info("[{}] All sessions suspended. Suspended count: {}",
                Instant.now(), suspendedSessions.size());
    }

    /**
     * Handles failure of a specific vehicle's consumption session.
     * The failure is isolated: other vehicles continue consuming without interruption (Req 12.5).
     * The failed session is removed from active sessions and logged.
     *
     * @param vehicleId the ID of the vehicle whose consumption failed
     * @param clientId  the client ID of the failed session
     * @param cause     the exception that caused the failure
     */
    public void handleVehicleFailure(String vehicleId, String clientId, Throwable cause) {
        String sessionKey = buildSessionKey(vehicleId, clientId);
        log.error("[{}] Isolated failure for vehicle '{}' (client: '{}'): {}. " +
                        "Other vehicles continue unaffected.",
                Instant.now(), vehicleId, clientId,
                cause != null ? cause.getMessage() : "unknown error", cause);

        ConsumptionSession failedSession = activeSessions.get(sessionKey);
        if (failedSession != null) {
            failedSession.deactivate();
            // Attempt reconnection with exponential backoff for the failed vehicle
            scheduleSessionRetry(failedSession);
        }
    }

    /**
     * Returns the current consumption mode.
     */
    public ConsumptionMode getCurrentMode() {
        return currentMode;
    }

    /**
     * Returns an unmodifiable view of the active sessions.
     */
    public Map<String, ConsumptionSession> getActiveSessions() {
        return Collections.unmodifiableMap(activeSessions);
    }

    /**
     * Returns an unmodifiable view of the suspended sessions (those awaiting resumption).
     */
    public Map<String, ConsumptionSession> getSuspendedSessions() {
        return Collections.unmodifiableMap(suspendedSessions);
    }

    /**
     * Returns the count of unique vehicles currently being consumed.
     */
    public int getActiveVehicleCount() {
        return (int) activeSessions.values().stream()
                .map(session -> session.getVehicle().getId())
                .distinct()
                .count();
    }

    /**
     * Returns whether Random Mode is currently active.
     */
    public boolean isRandomModeActive() {
        return randomModeActive;
    }

    /**
     * Returns the currently configured client count.
     */
    public int getClientCount() {
        return clientCount;
    }

    @PreDestroy
    public void shutdown() {
        log.info("[{}] Shutting down ConsumptionOrchestrator scheduler", Instant.now());
        scheduler.shutdownNow();
    }

    // --- Private helper methods ---

    /**
     * Starts a session for a single vehicle/client pair with isolated failure handling.
     * If the connection fails for this vehicle, it is logged and does not affect other vehicles (Req 12.5).
     *
     * @param vehicle the vehicle to create a session for
     * @param clientId the client identifier
     * @param mode the consumption mode
     */
    private void startSessionForVehicle(Vehicle vehicle, String clientId, ConsumptionMode mode) {
        String sessionKey = buildSessionKey(vehicle.getId(), clientId);

        if (activeSessions.containsKey(sessionKey)) {
            log.debug("[{}] Session already exists for key: {}", Instant.now(), sessionKey);
            return;
        }

        ConsumptionSession session = new ConsumptionSession(sessionKey, vehicle, clientId, mode);
        activeSessions.put(sessionKey, session);

        try {
            // Create WebSocket connection - isolated per vehicle
            clientPool.createConnection(vehicle, clientId);
            metricsCollector.recordConnectionChange(1);
            metricsCollector.recordVehicleActive(vehicle.getId());
            if (vehicle.getRealmId() != null) {
                metricsCollector.recordRealmActive(vehicle.getRealmId());
            }
            log.debug("[{}] Created session for vehicle={}, client={}, mode={}",
                    Instant.now(), vehicle.getId(), clientId, mode);
        } catch (Exception e) {
            // Isolated failure: log error but do NOT remove other sessions or stop other vehicles (Req 12.5)
            log.error("[{}] Failed to create session for vehicle '{}' (client: '{}'): {}. " +
                            "Other vehicles continue unaffected.",
                    Instant.now(), vehicle.getId(), clientId, e.getMessage(), e);
            // Keep the session in activeSessions for retry attempts
            scheduleSessionRetry(session);
        }
    }

    /**
     * Resumes a single session by re-establishing its WebSocket connection.
     *
     * @param session the session to resume
     */
    private void resumeSession(ConsumptionSession session) {
        Vehicle vehicle = session.getVehicle();
        String clientId = session.getClientId();

        log.info("[{}] Resuming session for vehicle '{}' (client: '{}')",
                Instant.now(), vehicle.getId(), clientId);

        clientPool.createConnection(vehicle, clientId);
        metricsCollector.recordConnectionChange(1);
        metricsCollector.recordVehicleActive(vehicle.getId());
        if (vehicle.getRealmId() != null) {
            metricsCollector.recordRealmActive(vehicle.getRealmId());
        }

        session.activate();
    }

    /**
     * Schedules a retry for a failed session using exponential backoff.
     * Retries up to the configured maximum number of attempts.
     *
     * @param session the failed session to retry
     */
    private void scheduleSessionRetry(ConsumptionSession session) {
        long initialDelay = configService.getRetryInitialDelay();
        long maxDelay = configService.getRetryMaxDelay();
        int maxAttempts = configService.getRetryMaxAttempts();

        scheduleSessionRetryWithBackoff(session, 0, initialDelay, maxDelay, maxAttempts);
    }

    private void scheduleSessionRetryWithBackoff(ConsumptionSession session, int attempt,
                                                  long initialDelay, long maxDelay, int maxAttempts) {
        if (attempt >= maxAttempts) {
            log.error("[{}] Session retry exhausted for vehicle '{}' (client: '{}') after {} attempts",
                    Instant.now(), session.getVehicle().getId(), session.getClientId(), maxAttempts);
            return;
        }

        long delay = Math.min(initialDelay * (1L << attempt), maxDelay);
        log.warn("[{}] Scheduling session retry for vehicle '{}' (client: '{}') in {}ms (attempt {}/{})",
                Instant.now(), session.getVehicle().getId(), session.getClientId(),
                delay, attempt + 1, maxAttempts);

        try {
            scheduler.schedule(() -> {
                String sessionKey = buildSessionKey(session.getVehicle().getId(), session.getClientId());
                if (!activeSessions.containsKey(sessionKey)) {
                    log.debug("[{}] Session no longer active, skipping retry: {}",
                            Instant.now(), sessionKey);
                    return;
                }
                try {
                    clientPool.createConnection(session.getVehicle(), session.getClientId());
                    session.activate();
                    log.info("[{}] Session retry successful for vehicle '{}' (client: '{}')",
                            Instant.now(), session.getVehicle().getId(), session.getClientId());
                } catch (Exception e) {
                    log.warn("[{}] Session retry attempt {} failed for vehicle '{}' (client: '{}'): {}",
                            Instant.now(), attempt + 1, session.getVehicle().getId(),
                            session.getClientId(), e.getMessage());
                    scheduleSessionRetryWithBackoff(session, attempt + 1, initialDelay, maxDelay, maxAttempts);
                }
            }, delay, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("[{}] Failed to schedule session retry for vehicle '{}': {}",
                    Instant.now(), session.getVehicle().getId(), e.getMessage());
        }
    }

    /**
     * Collects all vehicles from all cached realms via the DiscoveryService.
     */
    private List<Vehicle> getAllVehiclesFromRealms() {
        List<Realm> realms = discoveryService.getCachedRealms();
        List<Vehicle> allVehicles = new ArrayList<>();
        for (Realm realm : realms) {
            if (realm.getVehicles() != null && !realm.getVehicles().isEmpty()) {
                allVehicles.addAll(realm.getVehicles());
            } else {
                // Try per-realm vehicle cache
                List<Vehicle> cached = discoveryService.getCachedVehicles(realm.getId());
                allVehicles.addAll(cached);
            }
        }
        return allVehicles;
    }

    /**
     * Randomly selects a subset of vehicles from the given list.
     * Selects between 1 and the total number of vehicles (inclusive).
     */
    private List<Vehicle> selectRandomVehicles(List<Vehicle> allVehicles) {
        if (allVehicles.isEmpty()) {
            return Collections.emptyList();
        }

        int count = ThreadLocalRandom.current().nextInt(1, allVehicles.size() + 1);
        List<Vehicle> shuffled = new ArrayList<>(allVehicles);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        return shuffled.subList(0, count);
    }

    /**
     * Builds a session key from vehicle ID and client ID.
     *
     * @param vehicleId the vehicle identifier
     * @param clientId  the client identifier
     * @return session key in format "{vehicleId}_{clientId}"
     */
    private String buildSessionKey(String vehicleId, String clientId) {
        return vehicleId + "_" + clientId;
    }
}
