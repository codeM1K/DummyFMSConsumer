package com.fms.consumer.service;

import com.fms.consumer.integration.OpenRemoteRestClient;
import com.fms.consumer.integration.dto.RealmDTO;
import com.fms.consumer.integration.dto.VehicleDTO;
import com.fms.consumer.model.Realm;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.model.VehicleStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Service responsible for discovering available realms and vehicles from the Open Remote API.
 * Implements periodic refresh, retry logic on failure, and a listener pattern for notifying
 * interested components of discovery updates.
 */
@Service
public class DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

    private static final long RETRY_DELAY_SECONDS = 10;
    private static final long MAX_RETRY_DELAY_SECONDS = 30;

    private final AuthenticationService authService;
    private final OpenRemoteRestClient restClient;
    private final ConfigurationService configService;
    private final ScheduledExecutorService scheduler;

    private final ConcurrentHashMap<String, Realm> realmCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Vehicle>> vehicleCache = new ConcurrentHashMap<>();
    private final List<DiscoveryListener> listeners = new CopyOnWriteArrayList<>();

    private volatile ScheduledFuture<?> realmRefreshTask;
    private volatile ScheduledFuture<?> vehicleRefreshTask;

    public DiscoveryService(AuthenticationService authService,
                            OpenRemoteRestClient restClient,
                            ConfigurationService configService) {
        this.authService = authService;
        this.restClient = restClient;
        this.configService = configService;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "discovery-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Discovers all available realms from the Open Remote API.
     * Gets the session token from AuthenticationService, calls the REST client,
     * and converts RealmDTOs to Realm models.
     *
     * @return a CompletableFuture containing the list of discovered realms
     */
    public CompletableFuture<List<Realm>> discoverRealms() {
        log.info("[{}] Starting realm discovery", Instant.now());

        String sessionToken = authService.getSessionToken();
        if (sessionToken == null) {
            log.warn("[{}] No session token available, attempting authentication first", Instant.now());
            return authService.authenticate()
                    .thenCompose(result -> {
                        if (result.isSuccess()) {
                            return performRealmDiscovery(result.getSessionToken());
                        } else {
                            return CompletableFuture.failedFuture(
                                    new RuntimeException("Authentication failed: " + result.getErrorMessage()));
                        }
                    });
        }

        return performRealmDiscovery(sessionToken);
    }

    /**
     * Discovers all vehicles for a specific realm from the Open Remote API.
     * Gets the session token from AuthenticationService, calls the REST client,
     * and converts VehicleDTOs to Vehicle models.
     *
     * @param realmId the ID of the realm to discover vehicles for
     * @return a CompletableFuture containing the list of discovered vehicles
     */
    public CompletableFuture<List<Vehicle>> discoverVehicles(String realmId) {
        log.info("[{}] Starting vehicle discovery for realm '{}'", Instant.now(), realmId);

        String sessionToken = authService.getSessionToken();
        if (sessionToken == null) {
            log.warn("[{}] No session token available, attempting authentication first", Instant.now());
            return authService.authenticate()
                    .thenCompose(result -> {
                        if (result.isSuccess()) {
                            return performVehicleDiscovery(realmId, result.getSessionToken());
                        } else {
                            return CompletableFuture.failedFuture(
                                    new RuntimeException("Authentication failed: " + result.getErrorMessage()));
                        }
                    });
        }

        return performVehicleDiscovery(realmId, sessionToken);
    }

    /**
     * Starts periodic refresh of realms and vehicles using ScheduledExecutorService.
     * Realm refresh interval and vehicle refresh interval are read from ConfigurationService.
     */
    public void startPeriodicRefresh() {
        int realmInterval = configService.getRealmRefreshInterval();
        int vehicleInterval = configService.getVehicleRefreshInterval();

        log.info("[{}] Starting periodic discovery refresh - realms every {}s, vehicles every {}s",
                Instant.now(), realmInterval, vehicleInterval);

        realmRefreshTask = scheduler.scheduleAtFixedRate(
                this::refreshRealms,
                realmInterval,
                realmInterval,
                TimeUnit.SECONDS
        );

        vehicleRefreshTask = scheduler.scheduleAtFixedRate(
                this::refreshVehicles,
                vehicleInterval,
                vehicleInterval,
                TimeUnit.SECONDS
        );
    }

    /**
     * Stops the periodic refresh tasks.
     */
    public void stopPeriodicRefresh() {
        log.info("[{}] Stopping periodic discovery refresh", Instant.now());

        if (realmRefreshTask != null) {
            realmRefreshTask.cancel(false);
            realmRefreshTask = null;
        }

        if (vehicleRefreshTask != null) {
            vehicleRefreshTask.cancel(false);
            vehicleRefreshTask = null;
        }
    }

    /**
     * Registers a listener to receive discovery update notifications.
     *
     * @param listener the listener to register
     */
    public void addListener(DiscoveryListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(DiscoveryListener listener) {
        listeners.remove(listener);
    }

    /**
     * Returns a snapshot of the currently cached realms.
     *
     * @return unmodifiable list of cached realms
     */
    public List<Realm> getCachedRealms() {
        return Collections.unmodifiableList(new ArrayList<>(realmCache.values()));
    }

    /**
     * Returns a snapshot of the currently cached vehicles for a specific realm.
     *
     * @param realmId the realm ID
     * @return unmodifiable list of cached vehicles, or empty list if not cached
     */
    public List<Vehicle> getCachedVehicles(String realmId) {
        List<Vehicle> vehicles = vehicleCache.get(realmId);
        return vehicles != null ? Collections.unmodifiableList(new ArrayList<>(vehicles)) : Collections.emptyList();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down DiscoveryService scheduler");
        stopPeriodicRefresh();
        scheduler.shutdownNow();
    }

    // --- Private helper methods ---

    private CompletableFuture<List<Realm>> performRealmDiscovery(String sessionToken) {
        return restClient.getRealms(sessionToken)
                .thenApply(realmDTOs -> {
                    List<Realm> realms = realmDTOs.stream()
                            .map(this::convertToRealm)
                            .collect(Collectors.toList());

                    // Update cache
                    realmCache.clear();
                    for (Realm realm : realms) {
                        realmCache.put(realm.getId(), realm);
                    }

                    log.info("[{}] Realm discovery completed - found {} realms",
                            Instant.now(), realms.size());

                    // Notify listeners
                    notifyRealmsUpdated(realms);

                    return realms;
                })
                .exceptionally(throwable -> {
                    log.error("[{}] Realm discovery failed: {}",
                            Instant.now(), throwable.getMessage(), throwable);
                    notifyDiscoveryError(throwable);
                    throw new CompletionException(throwable);
                });
    }

    private CompletableFuture<List<Vehicle>> performVehicleDiscovery(String realmId, String sessionToken) {
        return restClient.getVehicles(realmId, sessionToken)
                .thenApply(vehicleDTOs -> {
                    List<Vehicle> vehicles = vehicleDTOs.stream()
                            .map(dto -> convertToVehicle(dto, realmId))
                            .collect(Collectors.toList());

                    // Update cache
                    vehicleCache.put(realmId, vehicles);

                    // Also update the realm's vehicle list if cached
                    Realm realm = realmCache.get(realmId);
                    if (realm != null) {
                        realm.setVehicles(vehicles);
                    }

                    log.info("[{}] Vehicle discovery completed for realm '{}' - found {} vehicles",
                            Instant.now(), realmId, vehicles.size());

                    // Notify listeners
                    notifyVehiclesUpdated(realmId, vehicles);

                    return vehicles;
                })
                .exceptionally(throwable -> {
                    log.error("[{}] Vehicle discovery failed for realm '{}': {}",
                            Instant.now(), realmId, throwable.getMessage(), throwable);
                    notifyDiscoveryError(throwable);
                    throw new CompletionException(throwable);
                });
    }

    /**
     * Refreshes all realms. Called periodically by the scheduler.
     * On failure, schedules a retry with exponential backoff (Req 12.1, 12.2 pattern).
     */
    private void refreshRealms() {
        try {
            discoverRealms()
                    .whenComplete((realms, throwable) -> {
                        if (throwable != null) {
                            log.error("[{}] Periodic realm refresh failed: {}. Scheduling retry with backoff.",
                                    Instant.now(), throwable.getMessage());
                            scheduleRealmRetryWithBackoff(0);
                        }
                    });
        } catch (Exception e) {
            log.error("[{}] Unexpected error during periodic realm refresh: {}",
                    Instant.now(), e.getMessage(), e);
            scheduleRealmRetryWithBackoff(0);
        }
    }

    /**
     * Refreshes vehicles for all cached realms. Called periodically by the scheduler.
     * On failure for a specific realm, schedules a retry without affecting other realms (Req 12.5).
     */
    private void refreshVehicles() {
        List<String> realmIds = new ArrayList<>(realmCache.keySet());
        for (String realmId : realmIds) {
            try {
                discoverVehicles(realmId)
                        .whenComplete((vehicles, throwable) -> {
                            if (throwable != null) {
                                log.error("[{}] Periodic vehicle refresh failed for realm '{}': {}. " +
                                                "Scheduling retry. Other realms unaffected.",
                                        Instant.now(), realmId, throwable.getMessage());
                                scheduleVehicleRetryWithBackoff(realmId, 0);
                            }
                        });
            } catch (Exception e) {
                // Isolated failure: log error for this realm and continue with others (Req 12.5)
                log.error("[{}] Unexpected error during periodic vehicle refresh for realm '{}': {}. " +
                                "Other realms unaffected.",
                        Instant.now(), realmId, e.getMessage(), e);
                scheduleVehicleRetryWithBackoff(realmId, 0);
            }
        }
    }

    /**
     * Schedules a realm discovery retry with exponential backoff.
     * Delay starts at RETRY_DELAY_SECONDS and doubles each attempt, capped at MAX_RETRY_DELAY_SECONDS.
     *
     * @param attempt the current retry attempt (0-indexed)
     */
    private void scheduleRealmRetryWithBackoff(int attempt) {
        long delay = Math.min(RETRY_DELAY_SECONDS * (1L << attempt), MAX_RETRY_DELAY_SECONDS);
        log.warn("[{}] Scheduling realm discovery retry in {}s (attempt {})",
                Instant.now(), delay, attempt + 1);

        scheduler.schedule(() -> {
            log.info("[{}] Retrying realm discovery (attempt {})", Instant.now(), attempt + 1);
            discoverRealms().whenComplete((realms, throwable) -> {
                if (throwable != null) {
                    log.error("[{}] Realm discovery retry attempt {} failed: {}",
                            Instant.now(), attempt + 1, throwable.getMessage());
                    if (attempt + 1 < 3) { // max 3 retry attempts for periodic refresh
                        scheduleRealmRetryWithBackoff(attempt + 1);
                    } else {
                        log.error("[{}] Realm discovery retries exhausted after {} attempts",
                                Instant.now(), attempt + 1);
                    }
                }
            });
        }, delay, TimeUnit.SECONDS);
    }

    /**
     * Schedules a vehicle discovery retry for a specific realm with exponential backoff.
     * Isolated per realm - failure does not affect other realm refreshes (Req 12.5).
     *
     * @param realmId the realm ID to retry
     * @param attempt the current retry attempt (0-indexed)
     */
    private void scheduleVehicleRetryWithBackoff(String realmId, int attempt) {
        long delay = Math.min(RETRY_DELAY_SECONDS * (1L << attempt), MAX_RETRY_DELAY_SECONDS);
        log.warn("[{}] Scheduling vehicle discovery retry for realm '{}' in {}s (attempt {})",
                Instant.now(), realmId, delay, attempt + 1);

        scheduler.schedule(() -> {
            log.info("[{}] Retrying vehicle discovery for realm '{}' (attempt {})",
                    Instant.now(), realmId, attempt + 1);
            discoverVehicles(realmId).whenComplete((vehicles, throwable) -> {
                if (throwable != null) {
                    log.error("[{}] Vehicle discovery retry attempt {} failed for realm '{}': {}",
                            Instant.now(), attempt + 1, realmId, throwable.getMessage());
                    if (attempt + 1 < 3) { // max 3 retry attempts for periodic refresh
                        scheduleVehicleRetryWithBackoff(realmId, attempt + 1);
                    } else {
                        log.error("[{}] Vehicle discovery retries exhausted for realm '{}' after {} attempts",
                                Instant.now(), realmId, attempt + 1);
                    }
                }
            });
        }, delay, TimeUnit.SECONDS);
    }

    private Realm convertToRealm(RealmDTO dto) {
        return new Realm(dto.getId(), dto.getName(), new ArrayList<>());
    }

    private Vehicle convertToVehicle(VehicleDTO dto, String realmId) {
        String vehicleRealmId = dto.getRealmId() != null ? dto.getRealmId() : realmId;
        return new Vehicle(dto.getId(), dto.getName(), vehicleRealmId, VehicleStatus.INACTIVE);
    }

    private void notifyRealmsUpdated(List<Realm> realms) {
        for (DiscoveryListener listener : listeners) {
            try {
                listener.onRealmsUpdated(realms);
            } catch (Exception e) {
                log.warn("Listener threw exception on realms update: {}", e.getMessage(), e);
            }
        }
    }

    private void notifyVehiclesUpdated(String realmId, List<Vehicle> vehicles) {
        for (DiscoveryListener listener : listeners) {
            try {
                listener.onVehiclesUpdated(realmId, vehicles);
            } catch (Exception e) {
                log.warn("Listener threw exception on vehicles update for realm '{}': {}",
                        realmId, e.getMessage(), e);
            }
        }
    }

    private void notifyDiscoveryError(Throwable error) {
        for (DiscoveryListener listener : listeners) {
            try {
                listener.onDiscoveryError(error);
            } catch (Exception e) {
                log.warn("Listener threw exception on discovery error notification: {}", e.getMessage(), e);
            }
        }
    }
}
