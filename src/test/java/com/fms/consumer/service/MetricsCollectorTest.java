package com.fms.consumer.service;

import com.fms.consumer.model.ClientMetrics;
import com.fms.consumer.model.ConsumptionMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MetricsCollector}.
 * Tests metrics aggregation accuracy, concurrent metric updates,
 * and metric reset/clearing behavior.
 *
 * Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6
 */
class MetricsCollectorTest {

    private MetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        metricsCollector = new MetricsCollector();
    }

    @AfterEach
    void tearDown() {
        metricsCollector.shutdown();
    }

    // --- Metrics Aggregation Accuracy ---

    @Test
    void getActiveConnections_returnsZero_initially() {
        assertEquals(0, metricsCollector.getActiveConnections());
    }

    @Test
    void getTotalUpdatesReceived_returnsZero_initially() {
        assertEquals(0, metricsCollector.getTotalUpdatesReceived());
    }

    @Test
    void getUpdatesPerSecond_returnsZero_initially() {
        assertEquals(0.0, metricsCollector.getUpdatesPerSecond());
    }

    @Test
    void getAggregateMetrics_returnsZeroCounts_initially() {
        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();

        assertEquals(0, metrics.getActiveConnections());
        assertEquals(0, metrics.getActiveVehicles());
        assertEquals(0, metrics.getActiveRealms());
        assertEquals(0.0, metrics.getUpdatesPerSecond());
        assertNotNull(metrics.getLastUpdate());
    }

    @Test
    void recordConnectionChange_incrementsActiveConnections() {
        metricsCollector.recordConnectionChange(1);
        metricsCollector.recordConnectionChange(1);
        metricsCollector.recordConnectionChange(1);

        assertEquals(3, metricsCollector.getActiveConnections());
    }

    @Test
    void recordConnectionChange_decrementsActiveConnections() {
        metricsCollector.recordConnectionChange(1);
        metricsCollector.recordConnectionChange(1);
        metricsCollector.recordConnectionChange(-1);

        assertEquals(1, metricsCollector.getActiveConnections());
    }

    @Test
    void recordLocationUpdate_incrementsTotalUpdatesReceived() {
        metricsCollector.recordLocationUpdate("vehicle-1", "client-1");
        metricsCollector.recordLocationUpdate("vehicle-2", "client-1");
        metricsCollector.recordLocationUpdate("vehicle-1", "client-2");

        assertEquals(3, metricsCollector.getTotalUpdatesReceived());
    }

    @Test
    void recordLocationUpdate_addsVehicleToActiveSet() {
        metricsCollector.recordLocationUpdate("vehicle-1", "client-1");
        metricsCollector.recordLocationUpdate("vehicle-2", "client-1");

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
        assertEquals(2, metrics.getActiveVehicles());
    }

    @Test
    void recordLocationUpdate_deduplicatesVehicleIds() {
        metricsCollector.recordLocationUpdate("vehicle-1", "client-1");
        metricsCollector.recordLocationUpdate("vehicle-1", "client-1");
        metricsCollector.recordLocationUpdate("vehicle-1", "client-2");

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
        // Same vehicle ID reported multiple times counts as one active vehicle
        assertEquals(1, metrics.getActiveVehicles());
    }

    @Test
    void recordVehicleActive_addsVehicleToActiveSet() {
        metricsCollector.recordVehicleActive("vehicle-1");
        metricsCollector.recordVehicleActive("vehicle-2");

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
        assertEquals(2, metrics.getActiveVehicles());
    }

    @Test
    void recordVehicleInactive_removesVehicleFromActiveSet() {
        metricsCollector.recordVehicleActive("vehicle-1");
        metricsCollector.recordVehicleActive("vehicle-2");
        metricsCollector.recordVehicleInactive("vehicle-1");

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
        assertEquals(1, metrics.getActiveVehicles());
    }

    @Test
    void recordVehicleActive_ignoresNullOrBlank() {
        metricsCollector.recordVehicleActive(null);
        metricsCollector.recordVehicleActive("");
        metricsCollector.recordVehicleActive("   ");

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
        assertEquals(0, metrics.getActiveVehicles());
    }

    @Test
    void recordVehicleInactive_ignoresNullOrBlank() {
        metricsCollector.recordVehicleActive("vehicle-1");
        metricsCollector.recordVehicleInactive(null);
        metricsCollector.recordVehicleInactive("");

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
        assertEquals(1, metrics.getActiveVehicles());
    }

    @Test
    void recordRealmActive_addsRealmToActiveSet() {
        metricsCollector.recordRealmActive("realm-A");
        metricsCollector.recordRealmActive("realm-B");

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
        assertEquals(2, metrics.getActiveRealms());
    }

    @Test
    void recordRealmInactive_removesRealmFromActiveSet() {
        metricsCollector.recordRealmActive("realm-A");
        metricsCollector.recordRealmActive("realm-B");
        metricsCollector.recordRealmInactive("realm-A");

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
        assertEquals(1, metrics.getActiveRealms());
    }

    @Test
    void recordRealmActive_ignoresNullOrBlank() {
        metricsCollector.recordRealmActive(null);
        metricsCollector.recordRealmActive("");
        metricsCollector.recordRealmActive("   ");

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
        assertEquals(0, metrics.getActiveRealms());
    }

    @Test
    void recordRealmInactive_ignoresNullOrBlank() {
        metricsCollector.recordRealmActive("realm-A");
        metricsCollector.recordRealmInactive(null);
        metricsCollector.recordRealmInactive("");

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
        assertEquals(1, metrics.getActiveRealms());
    }

    @Test
    void getPerClientMetrics_returnsEmptyMap_initially() {
        Map<String, ClientMetrics> perClient = metricsCollector.getPerClientMetrics();
        assertTrue(perClient.isEmpty());
    }

    @Test
    void recordLocationUpdate_createsPerClientMetrics() {
        metricsCollector.recordLocationUpdate("vehicle-1", "client-A");
        metricsCollector.recordLocationUpdate("vehicle-2", "client-B");

        Map<String, ClientMetrics> perClient = metricsCollector.getPerClientMetrics();
        assertEquals(2, perClient.size());
        assertTrue(perClient.containsKey("client-A"));
        assertTrue(perClient.containsKey("client-B"));
    }

    @Test
    void recordLocationUpdate_incrementsPerClientUpdateCount() {
        metricsCollector.recordLocationUpdate("vehicle-1", "client-A");
        metricsCollector.recordLocationUpdate("vehicle-2", "client-A");
        metricsCollector.recordLocationUpdate("vehicle-3", "client-A");

        Map<String, ClientMetrics> perClient = metricsCollector.getPerClientMetrics();
        assertEquals(3, perClient.get("client-A").getUpdatesReceived());
    }

    @Test
    void recordLocationUpdate_withNullClientId_doesNotCreateClientMetrics() {
        metricsCollector.recordLocationUpdate("vehicle-1", null);

        Map<String, ClientMetrics> perClient = metricsCollector.getPerClientMetrics();
        assertTrue(perClient.isEmpty());
    }

    @Test
    void recordLocationUpdate_withBlankClientId_doesNotCreateClientMetrics() {
        metricsCollector.recordLocationUpdate("vehicle-1", "   ");

        Map<String, ClientMetrics> perClient = metricsCollector.getPerClientMetrics();
        assertTrue(perClient.isEmpty());
    }

    @Test
    void getPerClientMetrics_returnsUnmodifiableMap() {
        metricsCollector.recordLocationUpdate("vehicle-1", "client-A");

        Map<String, ClientMetrics> perClient = metricsCollector.getPerClientMetrics();
        assertThrows(UnsupportedOperationException.class, () ->
                perClient.put("client-X", new ClientMetrics("client-X")));
    }

    @Test
    void updatesPerSecond_reflectsRecentUpdates() throws InterruptedException {
        // Record several updates
        for (int i = 0; i < 10; i++) {
            metricsCollector.recordLocationUpdate("vehicle-" + i, "client-1");
        }

        // Wait for the rate calculator to run (it runs every 1 second)
        Thread.sleep(1200);

        // After the rate calculation, updatesPerSecond should reflect the count
        // The first calculation takes the count accumulated since start
        double rate = metricsCollector.getUpdatesPerSecond();
        assertEquals(10.0, rate, 0.001,
                "Updates per second should reflect the 10 updates recorded in the previous interval");
    }

    @Test
    void updatesPerSecond_resetsToZero_whenNoNewUpdates() throws InterruptedException {
        // Record updates
        for (int i = 0; i < 5; i++) {
            metricsCollector.recordLocationUpdate("vehicle-" + i, "client-1");
        }

        // Wait for first rate calculation
        Thread.sleep(1200);
        assertTrue(metricsCollector.getUpdatesPerSecond() > 0);

        // Wait for second rate calculation with no new updates
        Thread.sleep(1200);
        assertEquals(0.0, metricsCollector.getUpdatesPerSecond(), 0.001,
                "Updates per second should be 0 when no new updates are received");
    }

    @Test
    void aggregateMetrics_combinedView_isConsistent() {
        // Set up a realistic scenario
        metricsCollector.recordConnectionChange(1);
        metricsCollector.recordConnectionChange(1);
        metricsCollector.recordRealmActive("realm-A");
        metricsCollector.recordRealmActive("realm-B");
        metricsCollector.recordVehicleActive("vehicle-1");
        metricsCollector.recordVehicleActive("vehicle-2");
        metricsCollector.recordVehicleActive("vehicle-3");

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();

        assertEquals(2, metrics.getActiveConnections());
        assertEquals(3, metrics.getActiveVehicles());
        assertEquals(2, metrics.getActiveRealms());
    }

    // --- Concurrent Metric Updates ---

    @Test
    void concurrentConnectionChanges_areThreadSafe() throws InterruptedException {
        int threadCount = 10;
        int incrementsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < incrementsPerThread; i++) {
                        metricsCollector.recordConnectionChange(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads at once
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        int expected = threadCount * incrementsPerThread;
        assertEquals(expected, metricsCollector.getActiveConnections(),
                "Concurrent connection changes must be thread-safe. Expected: " + expected);
    }

    @Test
    void concurrentLocationUpdates_areThreadSafe() throws InterruptedException {
        int threadCount = 10;
        int updatesPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < updatesPerThread; i++) {
                        metricsCollector.recordLocationUpdate(
                                "vehicle-" + threadId + "-" + i,
                                "client-" + threadId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        long expectedTotal = (long) threadCount * updatesPerThread;
        assertEquals(expectedTotal, metricsCollector.getTotalUpdatesReceived(),
                "All concurrent location updates must be counted");
    }

    @Test
    void concurrentRealmAndVehicleUpdates_areThreadSafe() throws InterruptedException {
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // Each thread activates unique realm and vehicle IDs
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    metricsCollector.recordRealmActive("realm-" + threadId);
                    metricsCollector.recordVehicleActive("vehicle-" + threadId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
        assertEquals(threadCount, metrics.getActiveRealms());
        assertEquals(threadCount, metrics.getActiveVehicles());
    }

    @Test
    void concurrentMixedOperations_areThreadSafe() throws InterruptedException {
        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // 2 threads adding connections
        for (int t = 0; t < 2; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 50; i++) {
                        metricsCollector.recordConnectionChange(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 2 threads recording location updates
        for (int t = 0; t < 2; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 50; i++) {
                        metricsCollector.recordLocationUpdate("vehicle-" + threadId + "-" + i, "client-" + threadId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 2 threads activating realms/vehicles
        for (int t = 0; t < 2; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 10; i++) {
                        metricsCollector.recordRealmActive("realm-" + threadId + "-" + i);
                        metricsCollector.recordVehicleActive("extra-vehicle-" + threadId + "-" + i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Verify connection count (2 threads * 50 increments = 100)
        assertEquals(100, metricsCollector.getActiveConnections());
        // Verify total updates (2 threads * 50 updates = 100)
        assertEquals(100, metricsCollector.getTotalUpdatesReceived());
    }

    // --- Metric Reset and Clearing ---

    @Test
    void reset_clearsActiveConnections() {
        metricsCollector.recordConnectionChange(1);
        metricsCollector.recordConnectionChange(1);
        metricsCollector.recordConnectionChange(1);

        metricsCollector.reset();

        assertEquals(0, metricsCollector.getActiveConnections());
    }

    @Test
    void reset_clearsTotalUpdatesReceived() {
        metricsCollector.recordLocationUpdate("vehicle-1", "client-1");
        metricsCollector.recordLocationUpdate("vehicle-2", "client-1");

        metricsCollector.reset();

        assertEquals(0, metricsCollector.getTotalUpdatesReceived());
    }

    @Test
    void reset_clearsUpdatesPerSecond() {
        // We can't easily wait for the scheduler, but we can test the reset directly
        metricsCollector.reset();

        assertEquals(0.0, metricsCollector.getUpdatesPerSecond());
    }

    @Test
    void reset_clearsActiveVehicles() {
        metricsCollector.recordVehicleActive("vehicle-1");
        metricsCollector.recordVehicleActive("vehicle-2");

        metricsCollector.reset();

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
        assertEquals(0, metrics.getActiveVehicles());
    }

    @Test
    void reset_clearsActiveRealms() {
        metricsCollector.recordRealmActive("realm-A");
        metricsCollector.recordRealmActive("realm-B");

        metricsCollector.reset();

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
        assertEquals(0, metrics.getActiveRealms());
    }

    @Test
    void reset_clearsPerClientMetrics() {
        metricsCollector.recordLocationUpdate("vehicle-1", "client-A");
        metricsCollector.recordLocationUpdate("vehicle-2", "client-B");

        metricsCollector.reset();

        Map<String, ClientMetrics> perClient = metricsCollector.getPerClientMetrics();
        assertTrue(perClient.isEmpty());
    }

    @Test
    void reset_bringsAllMetricsToZero() {
        // Set up state across all metrics
        metricsCollector.recordConnectionChange(1);
        metricsCollector.recordConnectionChange(1);
        metricsCollector.recordLocationUpdate("vehicle-1", "client-A");
        metricsCollector.recordLocationUpdate("vehicle-2", "client-B");
        metricsCollector.recordRealmActive("realm-A");
        metricsCollector.recordVehicleActive("vehicle-3");

        metricsCollector.reset();

        assertEquals(0, metricsCollector.getActiveConnections());
        assertEquals(0, metricsCollector.getTotalUpdatesReceived());
        assertEquals(0.0, metricsCollector.getUpdatesPerSecond());

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
        assertEquals(0, metrics.getActiveConnections());
        assertEquals(0, metrics.getActiveVehicles());
        assertEquals(0, metrics.getActiveRealms());
        assertEquals(0.0, metrics.getUpdatesPerSecond());

        assertTrue(metricsCollector.getPerClientMetrics().isEmpty());
    }

    @Test
    void metricsCanBeReaccumulated_afterReset() {
        // First accumulate
        metricsCollector.recordConnectionChange(1);
        metricsCollector.recordLocationUpdate("vehicle-1", "client-1");
        metricsCollector.recordRealmActive("realm-A");

        // Reset
        metricsCollector.reset();

        // Re-accumulate
        metricsCollector.recordConnectionChange(1);
        metricsCollector.recordConnectionChange(1);
        metricsCollector.recordLocationUpdate("vehicle-2", "client-2");
        metricsCollector.recordRealmActive("realm-B");
        metricsCollector.recordVehicleActive("vehicle-3");

        assertEquals(2, metricsCollector.getActiveConnections());
        assertEquals(1, metricsCollector.getTotalUpdatesReceived());

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
        assertEquals(2, metrics.getActiveConnections());
        assertEquals(1, metrics.getActiveRealms());
        // vehicle-2 from recordLocationUpdate + vehicle-3 from recordVehicleActive
        assertEquals(2, metrics.getActiveVehicles());
    }

    // --- Shutdown ---

    @Test
    void shutdown_stopsScheduler_gracefully() {
        // Should not throw
        assertDoesNotThrow(() -> metricsCollector.shutdown());
    }

    @Test
    void shutdown_canBeCalledMultipleTimes_safely() {
        // Should not throw on double shutdown
        assertDoesNotThrow(() -> {
            metricsCollector.shutdown();
            metricsCollector.shutdown();
        });
    }
}
