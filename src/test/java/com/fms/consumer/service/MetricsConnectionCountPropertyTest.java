package com.fms.consumer.service;

import com.fms.consumer.model.ConsumptionMetrics;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.lifecycle.BeforeTry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for active connection count in {@link MetricsCollector}.
 *
 * <p><b>Validates: Requirements 9.1</b></p>
 *
 * <p>Property 27: Active Connection Count Display —
 * "For any number of active WebSocket connections, the metrics panel SHALL
 * display the correct total count."</p>
 */
class MetricsConnectionCountPropertyTest {

    private MetricsCollector metricsCollector;

    @BeforeProperty
    void setUp() {
        metricsCollector = new MetricsCollector();
    }

    @AfterProperty
    void tearDown() {
        if (metricsCollector != null) {
            metricsCollector.shutdown();
        }
    }

    @BeforeTry
    void resetBeforeTry() {
        metricsCollector.reset();
    }

    /**
     * For any number of connections established (positive deltas), the aggregate metrics
     * SHALL report exactly that total as the active connection count.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 200)
    void activeConnectionCount_equalsNumberOfConnectionsEstablished(
            @ForAll("connectionCounts") int connectionCount) {

        // Establish connectionCount connections (each with delta +1)
        for (int i = 0; i < connectionCount; i++) {
            metricsCollector.recordConnectionChange(1);
        }

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();

        assertEquals(connectionCount, metrics.getActiveConnections(),
                "Active connection count should equal the number of connections established. " +
                        "Expected: " + connectionCount + ", got: " + metrics.getActiveConnections());
    }

    /**
     * For any number of connections established then closed, the aggregate metrics
     * SHALL report the correct remaining count (established - closed).
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 200)
    void activeConnectionCount_reflectsEstablishedMinusClosed(
            @ForAll("connectionCounts") int established,
            @ForAll("closedFraction") double closedFraction) {

        int toClose = (int) (established * closedFraction);

        // Establish connections
        for (int i = 0; i < established; i++) {
            metricsCollector.recordConnectionChange(1);
        }

        // Close some connections
        for (int i = 0; i < toClose; i++) {
            metricsCollector.recordConnectionChange(-1);
        }

        int expectedActive = established - toClose;
        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();

        assertEquals(expectedActive, metrics.getActiveConnections(),
                "Active connections should be established - closed. " +
                        "Established: " + established + ", closed: " + toClose +
                        ", expected: " + expectedActive + ", got: " + metrics.getActiveConnections());
    }

    /**
     * After closing all established connections, the active connection count SHALL be 0.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 200)
    void closingAllConnections_countsZero(
            @ForAll("connectionCounts") int connectionCount) {

        // Establish connections
        for (int i = 0; i < connectionCount; i++) {
            metricsCollector.recordConnectionChange(1);
        }

        // Close all connections
        for (int i = 0; i < connectionCount; i++) {
            metricsCollector.recordConnectionChange(-1);
        }

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();

        assertEquals(0, metrics.getActiveConnections(),
                "After closing all connections, count should be 0, but got: " +
                        metrics.getActiveConnections());
    }

    /**
     * The getActiveConnections() direct accessor SHALL always agree with
     * getAggregateMetrics().getActiveConnections() for any sequence of changes.
     *
     * <p><b>Validates: Requirements 9.1</b></p>
     */
    @Property(tries = 200)
    void directAccessor_matchesAggregateMetrics(
            @ForAll("connectionCounts") int established,
            @ForAll("closedFraction") double closedFraction) {

        int toClose = (int) (established * closedFraction);

        for (int i = 0; i < established; i++) {
            metricsCollector.recordConnectionChange(1);
        }
        for (int i = 0; i < toClose; i++) {
            metricsCollector.recordConnectionChange(-1);
        }

        int directValue = metricsCollector.getActiveConnections();
        int aggregateValue = metricsCollector.getAggregateMetrics().getActiveConnections();

        assertEquals(directValue, aggregateValue,
                "Direct accessor and aggregate metrics must agree. " +
                        "Direct: " + directValue + ", aggregate: " + aggregateValue);
    }

    /**
     * Generates connection counts between 0 and 100.
     */
    @Provide
    Arbitrary<Integer> connectionCounts() {
        return Arbitraries.integers().between(0, 100);
    }

    /**
     * Generates a fraction between 0.0 and 1.0 representing what proportion of
     * connections to close.
     */
    @Provide
    Arbitrary<Double> closedFraction() {
        return Arbitraries.doubles().between(0.0, 1.0);
    }
}
