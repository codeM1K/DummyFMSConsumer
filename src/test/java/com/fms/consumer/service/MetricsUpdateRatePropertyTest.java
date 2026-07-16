package com.fms.consumer.service;

import com.fms.consumer.model.ConsumptionMetrics;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for {@link MetricsCollector} update rate calculation.
 *
 * <p><b>Validates: Requirements 9.2</b></p>
 *
 * <p>Property 28: Update Rate Display —
 * "For any rate of Location_Data updates per second, the metrics panel SHALL display
 * the correct throughput value."</p>
 *
 * <p>Since the rate calculation runs on a 1-second scheduler, we focus on:
 * <ul>
 *   <li>getTotalUpdatesReceived() accumulates all calls immediately (no scheduler dependency)</li>
 *   <li>After the scheduler runs, getUpdatesPerSecond() reflects the count of updates in the last window</li>
 *   <li>After no updates in a window, the rate drops to 0</li>
 * </ul>
 * </p>
 */
class MetricsUpdateRatePropertyTest {

    private MetricsCollector metricsCollector;

    @BeforeTry
    void setUp() {
        metricsCollector = new MetricsCollector();
    }

    @AfterTry
    void tearDown() {
        if (metricsCollector != null) {
            metricsCollector.shutdown();
        }
    }

    /**
     * Property 28: For any number of recordLocationUpdate() calls,
     * getTotalUpdatesReceived() SHALL immediately reflect the total count
     * regardless of rate calculation windows.
     *
     * <p><b>Validates: Requirements 9.2</b></p>
     */
    @Property(tries = 50)
    void totalUpdatesReceived_accumulatesAllCalls(
            @ForAll @IntRange(min = 1, max = 100) int updateCount) {

        for (int i = 0; i < updateCount; i++) {
            metricsCollector.recordLocationUpdate("vehicle-" + i, "client-1");
        }

        assertEquals(updateCount, metricsCollector.getTotalUpdatesReceived(),
                "getTotalUpdatesReceived() must equal the number of recordLocationUpdate() calls");
    }

    /**
     * Property 28: For any number of recordLocationUpdate() calls made within a 1-second window,
     * after the rate calculation scheduler runs, getAggregateMetrics().getUpdatesPerSecond()
     * SHALL reflect the count of updates made in that window.
     *
     * <p><b>Validates: Requirements 9.2</b></p>
     */
    @Property(tries = 5)
    void updatesPerSecond_reflectsCountAfterSchedulerRuns(
            @ForAll @IntRange(min = 1, max = 50) int updateCount) throws InterruptedException {

        // Record updates within a short burst (well within a 1-second window)
        for (int i = 0; i < updateCount; i++) {
            metricsCollector.recordLocationUpdate("vehicle-" + i, "client-1");
        }

        // Wait long enough for the scheduler to run (it runs every 1 second, initial delay 1 second)
        // Use polling to avoid flaky timing issues
        double rate = 0.0;
        for (int attempt = 0; attempt < 10; attempt++) {
            Thread.sleep(300);
            rate = metricsCollector.getUpdatesPerSecond();
            if (rate > 0) {
                break;
            }
        }

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();

        // The rate should be equal to updateCount (the scheduler captures the count and resets)
        assertEquals((double) updateCount, rate, 0.001,
                "After scheduler runs, updatesPerSecond must equal the count of updates " +
                        "made in the previous window. Expected " + updateCount + " but got " + rate);
        assertEquals(rate, metrics.getUpdatesPerSecond(), 0.001,
                "getAggregateMetrics().getUpdatesPerSecond() must match getUpdatesPerSecond()");
    }

    /**
     * Property 28: After no updates occur in a rate calculation window,
     * the updatesPerSecond rate SHALL drop to 0.
     *
     * <p><b>Validates: Requirements 9.2</b></p>
     */
    @Property(tries = 5)
    void updatesPerSecond_dropsToZeroAfterNoUpdates(
            @ForAll @IntRange(min = 1, max = 30) int initialCount) throws InterruptedException {

        // Record some initial updates
        for (int i = 0; i < initialCount; i++) {
            metricsCollector.recordLocationUpdate("vehicle-" + i, "client-1");
        }

        // Wait for first scheduler run to capture the initial burst (poll for it)
        for (int attempt = 0; attempt < 10; attempt++) {
            Thread.sleep(300);
            if (metricsCollector.getUpdatesPerSecond() > 0) {
                break;
            }
        }

        // Confirm rate was set from initial burst
        double rateAfterBurst = metricsCollector.getUpdatesPerSecond();
        assertEquals((double) initialCount, rateAfterBurst, 0.001,
                "Rate after burst should be " + initialCount);

        // Wait for at least one more scheduler run with no new updates (poll for it to drop)
        double rateAfterIdle = rateAfterBurst;
        for (int attempt = 0; attempt < 10; attempt++) {
            Thread.sleep(300);
            rateAfterIdle = metricsCollector.getUpdatesPerSecond();
            if (rateAfterIdle == 0.0) {
                break;
            }
        }

        // Rate should now be 0 since no updates occurred in the second window
        assertEquals(0.0, rateAfterIdle, 0.001,
                "After no updates in a window, updatesPerSecond must drop to 0, but got " + rateAfterIdle);
    }

    /**
     * Property 28: getTotalUpdatesReceived() SHALL accumulate across multiple
     * rate calculation windows without being reset by the scheduler.
     *
     * <p><b>Validates: Requirements 9.2</b></p>
     */
    @Property(tries = 5)
    void totalUpdatesReceived_persistsAcrossWindows(
            @ForAll @IntRange(min = 1, max = 20) int firstBatch,
            @ForAll @IntRange(min = 1, max = 20) int secondBatch) throws InterruptedException {

        // First batch of updates
        for (int i = 0; i < firstBatch; i++) {
            metricsCollector.recordLocationUpdate("vehicle-" + i, "client-1");
        }

        // Wait for scheduler to capture first batch
        for (int attempt = 0; attempt < 10; attempt++) {
            Thread.sleep(300);
            if (metricsCollector.getUpdatesPerSecond() > 0) {
                break;
            }
        }

        // Second batch of updates
        for (int i = 0; i < secondBatch; i++) {
            metricsCollector.recordLocationUpdate("vehicle-" + (firstBatch + i), "client-1");
        }

        // Total should be cumulative regardless of windows
        long total = metricsCollector.getTotalUpdatesReceived();
        assertEquals(firstBatch + secondBatch, total,
                "getTotalUpdatesReceived() must accumulate across windows. " +
                        "Expected " + (firstBatch + secondBatch) + " but got " + total);
    }
}
