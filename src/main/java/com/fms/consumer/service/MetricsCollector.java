package com.fms.consumer.service;

import com.fms.consumer.model.ClientMetrics;
import com.fms.consumer.model.ConsumptionMetrics;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects and aggregates consumption metrics for the fleet tracking application.
 * Tracks active connections, throughput, update rates, and per-client metrics
 * in multi-client mode. Uses atomic types and concurrent collections for thread safety.
 * A scheduled executor calculates the updates-per-second rate every 1 second.
 */
@Service
public class MetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(MetricsCollector.class);

    private final AtomicInteger activeConnections;
    private final AtomicLong totalUpdatesReceived;
    private final AtomicLong updatesInLastSecond;
    private volatile double updatesPerSecond;

    private final ConcurrentHashMap.KeySetView<String, Boolean> activeVehicleIds;
    private final ConcurrentHashMap.KeySetView<String, Boolean> activeRealmIds;
    private final ConcurrentHashMap<String, ClientMetrics> perClientMetrics;

    private final ScheduledExecutorService scheduler;

    public MetricsCollector() {
        this.activeConnections = new AtomicInteger(0);
        this.totalUpdatesReceived = new AtomicLong(0);
        this.updatesInLastSecond = new AtomicLong(0);
        this.updatesPerSecond = 0.0;

        this.activeVehicleIds = ConcurrentHashMap.newKeySet();
        this.activeRealmIds = ConcurrentHashMap.newKeySet();
        this.perClientMetrics = new ConcurrentHashMap<>();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-rate-calculator");
            t.setDaemon(true);
            return t;
        });

        // Calculate updates per second every 1 second
        scheduler.scheduleAtFixedRate(this::calculateRate, 1, 1, TimeUnit.SECONDS);
        log.info("MetricsCollector initialized with 1-second rate calculation");
    }

    /**
     * Records a location update for the given vehicle and client.
     * Increments total update counters and per-client metrics.
     *
     * @param vehicleId the ID of the vehicle that sent the update
     * @param clientId  the ID of the client consuming the update
     */
    public void recordLocationUpdate(String vehicleId, String clientId) {
        totalUpdatesReceived.incrementAndGet();
        updatesInLastSecond.incrementAndGet();
        activeVehicleIds.add(vehicleId);

        if (clientId != null && !clientId.isBlank()) {
            perClientMetrics.computeIfAbsent(clientId, ClientMetrics::new)
                    .setUpdatesReceived(
                            perClientMetrics.get(clientId).getUpdatesReceived() + 1
                    );
        }

        log.debug("Recorded location update for vehicle={}, client={}", vehicleId, clientId);
    }

    /**
     * Adjusts the active connection count by the given delta.
     * Use +1 for a new connection, -1 for a closed connection.
     *
     * @param delta the change in active connections (+1 or -1 typically)
     */
    public void recordConnectionChange(int delta) {
        int newValue = activeConnections.addAndGet(delta);
        log.debug("Connection change: delta={}, activeConnections={}", delta, newValue);
    }

    /**
     * Records a realm as actively consumed.
     *
     * @param realmId the realm ID to add to the active set
     */
    public void recordRealmActive(String realmId) {
        if (realmId != null && !realmId.isBlank()) {
            activeRealmIds.add(realmId);
            log.debug("Realm activated: {}", realmId);
        }
    }

    /**
     * Records a realm as no longer actively consumed.
     *
     * @param realmId the realm ID to remove from the active set
     */
    public void recordRealmInactive(String realmId) {
        if (realmId != null && !realmId.isBlank()) {
            activeRealmIds.remove(realmId);
            log.debug("Realm deactivated: {}", realmId);
        }
    }

    /**
     * Records a vehicle as actively consumed.
     *
     * @param vehicleId the vehicle ID to add to the active set
     */
    public void recordVehicleActive(String vehicleId) {
        if (vehicleId != null && !vehicleId.isBlank()) {
            activeVehicleIds.add(vehicleId);
            log.debug("Vehicle activated: {}", vehicleId);
        }
    }

    /**
     * Records a vehicle as no longer actively consumed.
     *
     * @param vehicleId the vehicle ID to remove from the active set
     */
    public void recordVehicleInactive(String vehicleId) {
        if (vehicleId != null && !vehicleId.isBlank()) {
            activeVehicleIds.remove(vehicleId);
            log.debug("Vehicle deactivated: {}", vehicleId);
        }
    }

    /**
     * Returns current aggregate consumption metrics.
     *
     * @return a {@link ConsumptionMetrics} snapshot of the current state
     */
    public ConsumptionMetrics getAggregateMetrics() {
        ConsumptionMetrics metrics = new ConsumptionMetrics(
                activeConnections.get(),
                activeVehicleIds.size(),
                activeRealmIds.size(),
                updatesPerSecond
        );
        metrics.setLastUpdate(Instant.now());
        return metrics;
    }

    /**
     * Returns per-client metrics for multi-client mode.
     *
     * @return an unmodifiable map of client ID to {@link ClientMetrics}
     */
    public Map<String, ClientMetrics> getPerClientMetrics() {
        return Collections.unmodifiableMap(perClientMetrics);
    }

    /**
     * Resets all metrics to their initial values.
     * Useful when stopping all consumption sessions.
     */
    public void reset() {
        activeConnections.set(0);
        totalUpdatesReceived.set(0);
        updatesInLastSecond.set(0);
        updatesPerSecond = 0.0;
        activeVehicleIds.clear();
        activeRealmIds.clear();
        perClientMetrics.clear();
        log.info("All metrics have been reset");
    }

    /**
     * Returns the total number of location updates received since startup or last reset.
     */
    public long getTotalUpdatesReceived() {
        return totalUpdatesReceived.get();
    }

    /**
     * Returns the current updates-per-second rate.
     */
    public double getUpdatesPerSecond() {
        return updatesPerSecond;
    }

    /**
     * Returns the current number of active connections.
     */
    public int getActiveConnections() {
        return activeConnections.get();
    }

    /**
     * Calculates the updates-per-second rate. Called every 1 second by the scheduler.
     * Takes the count of updates received in the last second and resets the counter.
     */
    private void calculateRate() {
        long count = updatesInLastSecond.getAndSet(0);
        updatesPerSecond = count;
        log.trace("Rate calculation: updatesPerSecond={}", updatesPerSecond);
    }

    /**
     * Shuts down the scheduler on application destruction.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down MetricsCollector scheduler");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
