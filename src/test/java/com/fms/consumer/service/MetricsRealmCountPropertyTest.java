package com.fms.consumer.service;

import com.fms.consumer.model.ConsumptionMetrics;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for active realm count in {@link MetricsCollector}.
 *
 * <p><b>Validates: Requirements 9.4</b></p>
 *
 * <p>Property 30: Active Realm Count Display —
 * "For any number of realms with at least one active vehicle consumption,
 * the metrics panel SHALL display the correct count."</p>
 */
class MetricsRealmCountPropertyTest {

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
     * For any set of unique realm IDs activated, getAggregateMetrics().getActiveRealms()
     * equals the set size.
     *
     * <p><b>Validates: Requirements 9.4</b></p>
     */
    @Property(tries = 200)
    void activeRealmCount_equalsUniqueRealmSetSize(
            @ForAll("realmIdSets") Set<String> realmIds) {

        // Activate all realms in the set
        for (String realmId : realmIds) {
            metricsCollector.recordRealmActive(realmId);
        }

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();

        assertEquals(realmIds.size(), metrics.getActiveRealms(),
                "Active realm count should equal the number of unique realm IDs activated. " +
                        "Expected: " + realmIds.size() + ", got: " + metrics.getActiveRealms());
    }

    /**
     * Activating the same realm multiple times does not increase the count (it's a set).
     *
     * <p><b>Validates: Requirements 9.4</b></p>
     */
    @Property(tries = 200)
    void duplicateActivation_doesNotIncreaseCount(
            @ForAll("realmIdSets") Set<String> realmIds,
            @ForAll("duplicateCounts") int duplicateCount) {

        // Activate all realms
        for (String realmId : realmIds) {
            metricsCollector.recordRealmActive(realmId);
        }

        // Activate all realms again multiple times
        for (int i = 0; i < duplicateCount; i++) {
            for (String realmId : realmIds) {
                metricsCollector.recordRealmActive(realmId);
            }
        }

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();

        assertEquals(realmIds.size(), metrics.getActiveRealms(),
                "Activating the same realms " + (duplicateCount + 1) + " times should not increase count. " +
                        "Expected: " + realmIds.size() + ", got: " + metrics.getActiveRealms());
    }

    /**
     * After deactivating a realm, the count decreases by 1.
     *
     * <p><b>Validates: Requirements 9.4</b></p>
     */
    @Property(tries = 200)
    void deactivatingRealm_decreasesCountByOne(
            @ForAll("realmIdSets") Set<String> realmIds) {

        // Need at least one realm to deactivate
        Assume.that(!realmIds.isEmpty());

        // Activate all realms
        for (String realmId : realmIds) {
            metricsCollector.recordRealmActive(realmId);
        }

        int initialCount = metricsCollector.getAggregateMetrics().getActiveRealms();

        // Deactivate one realm
        String realmToDeactivate = realmIds.iterator().next();
        metricsCollector.recordRealmInactive(realmToDeactivate);

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();

        assertEquals(initialCount - 1, metrics.getActiveRealms(),
                "After deactivating one realm, count should decrease by 1. " +
                        "Expected: " + (initialCount - 1) + ", got: " + metrics.getActiveRealms());
    }

    /**
     * After deactivating all realms, count is 0.
     *
     * <p><b>Validates: Requirements 9.4</b></p>
     */
    @Property(tries = 200)
    void deactivatingAllRealms_countsZero(
            @ForAll("realmIdSets") Set<String> realmIds) {

        // Activate all realms
        for (String realmId : realmIds) {
            metricsCollector.recordRealmActive(realmId);
        }

        // Deactivate all realms
        for (String realmId : realmIds) {
            metricsCollector.recordRealmInactive(realmId);
        }

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();

        assertEquals(0, metrics.getActiveRealms(),
                "After deactivating all realms, count should be 0, but got: " + metrics.getActiveRealms());
    }

    /**
     * Generates sets of realm IDs with 1-15 items.
     */
    @Provide
    Arbitrary<Set<String>> realmIdSets() {
        Arbitrary<String> realmId = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(3)
                .ofMaxLength(10)
                .map(s -> "REALM-" + s);

        return realmId.set().ofMinSize(1).ofMaxSize(15);
    }

    /**
     * Generates a number of times to duplicate activations (1-5).
     */
    @Provide
    Arbitrary<Integer> duplicateCounts() {
        return Arbitraries.integers().between(1, 5);
    }
}
