package com.fms.consumer.service;

import com.fms.consumer.model.ConsumptionMetrics;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for active vehicle count in {@link MetricsCollector}.
 *
 * <p><b>Validates: Requirements 9.3</b></p>
 *
 * <p>Property 29: Active Vehicle Count Display —
 * "For any number of vehicles currently being consumed, the metrics panel
 * SHALL display the correct count."</p>
 */
class MetricsVehicleCountPropertyTest {

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
     * For any set of unique vehicle IDs activated, getAggregateMetrics().getActiveVehicles()
     * equals the set size.
     *
     * <p><b>Validates: Requirements 9.3</b></p>
     */
    @Property(tries = 200)
    void activeVehicleCount_equalsUniqueVehicleSetSize(
            @ForAll("vehicleIdSets") Set<String> vehicleIds) {

        // Activate all vehicles in the set
        for (String vehicleId : vehicleIds) {
            metricsCollector.recordVehicleActive(vehicleId);
        }

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();

        assertEquals(vehicleIds.size(), metrics.getActiveVehicles(),
                "Active vehicle count should equal the number of unique vehicle IDs activated. " +
                        "Expected: " + vehicleIds.size() + ", got: " + metrics.getActiveVehicles());
    }

    /**
     * Activating the same vehicle multiple times does not increase the count (it's a set).
     *
     * <p><b>Validates: Requirements 9.3</b></p>
     */
    @Property(tries = 200)
    void duplicateActivation_doesNotIncreaseCount(
            @ForAll("vehicleIdSets") Set<String> vehicleIds,
            @ForAll("duplicateCounts") int duplicateCount) {

        // Activate all vehicles
        for (String vehicleId : vehicleIds) {
            metricsCollector.recordVehicleActive(vehicleId);
        }

        // Activate all vehicles again multiple times
        for (int i = 0; i < duplicateCount; i++) {
            for (String vehicleId : vehicleIds) {
                metricsCollector.recordVehicleActive(vehicleId);
            }
        }

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();

        assertEquals(vehicleIds.size(), metrics.getActiveVehicles(),
                "Activating the same vehicles " + (duplicateCount + 1) + " times should not increase count. " +
                        "Expected: " + vehicleIds.size() + ", got: " + metrics.getActiveVehicles());
    }

    /**
     * After deactivating a vehicle, the count decreases by 1.
     *
     * <p><b>Validates: Requirements 9.3</b></p>
     */
    @Property(tries = 200)
    void deactivatingVehicle_decreasesCountByOne(
            @ForAll("vehicleIdSets") Set<String> vehicleIds) {

        // Need at least one vehicle to deactivate
        Assume.that(!vehicleIds.isEmpty());

        // Activate all vehicles
        for (String vehicleId : vehicleIds) {
            metricsCollector.recordVehicleActive(vehicleId);
        }

        int initialCount = metricsCollector.getAggregateMetrics().getActiveVehicles();

        // Deactivate one vehicle
        String vehicleToDeactivate = vehicleIds.iterator().next();
        metricsCollector.recordVehicleInactive(vehicleToDeactivate);

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();

        assertEquals(initialCount - 1, metrics.getActiveVehicles(),
                "After deactivating one vehicle, count should decrease by 1. " +
                        "Expected: " + (initialCount - 1) + ", got: " + metrics.getActiveVehicles());
    }

    /**
     * After deactivating all vehicles, count is 0.
     *
     * <p><b>Validates: Requirements 9.3</b></p>
     */
    @Property(tries = 200)
    void deactivatingAllVehicles_countsZero(
            @ForAll("vehicleIdSets") Set<String> vehicleIds) {

        // Activate all vehicles
        for (String vehicleId : vehicleIds) {
            metricsCollector.recordVehicleActive(vehicleId);
        }

        // Deactivate all vehicles
        for (String vehicleId : vehicleIds) {
            metricsCollector.recordVehicleInactive(vehicleId);
        }

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();

        assertEquals(0, metrics.getActiveVehicles(),
                "After deactivating all vehicles, count should be 0, but got: " + metrics.getActiveVehicles());
    }

    /**
     * Generates sets of vehicle IDs with 1-20 items.
     */
    @Provide
    Arbitrary<Set<String>> vehicleIdSets() {
        Arbitrary<String> vehicleId = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(3)
                .ofMaxLength(12)
                .map(s -> "VEH-" + s);

        return vehicleId.set().ofMinSize(1).ofMaxSize(20);
    }

    /**
     * Generates a number of times to duplicate activations (1-5).
     */
    @Provide
    Arbitrary<Integer> duplicateCounts() {
        return Arbitraries.integers().between(1, 5);
    }
}
