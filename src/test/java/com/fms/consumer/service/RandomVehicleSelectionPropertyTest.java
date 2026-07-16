package com.fms.consumer.service;

import com.fms.consumer.integration.WebSocketClientPool;
import com.fms.consumer.model.*;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for random vehicle selection in {@link ConsumptionOrchestrator}.
 *
 * <p><b>Validates: Requirements 4.1</b></p>
 *
 * <p>Property 9: Random Vehicle Selection —
 * "For any available set of realms and vehicles when Random Mode is activated,
 * the system SHALL select vehicles randomly across all realms."</p>
 */
class RandomVehicleSelectionPropertyTest {

    private WebSocketClientPool clientPool; private LocationPollingService locationPollingService;
    private MetricsCollector metricsCollector;
    private DiscoveryService discoveryService;
    private ConfigurationService configService;
    private ConsumptionOrchestrator orchestrator;

    @BeforeTry
    void setUp() {
        clientPool = mock(WebSocketClientPool.class); locationPollingService = mock(LocationPollingService.class);
        metricsCollector = mock(MetricsCollector.class);
        discoveryService = mock(DiscoveryService.class);
        configService = mock(ConfigurationService.class);

        when(configService.getSimulationMaxClients()).thenReturn(100);

        orchestrator = new ConsumptionOrchestrator(clientPool, locationPollingService, metricsCollector, discoveryService, configService);
    }

    /**
     * For any non-empty set of vehicles across realms, when startRandomMode is called,
     * the selected vehicles (active sessions) SHALL be a non-empty subset of the
     * available vehicles.
     *
     * <p><b>Validates: Requirements 4.1</b></p>
     */
    @Property(tries = 200)
    void selectedVehicles_areNonEmptySubsetOfAvailableVehicles(
            @ForAll("realmsWithVehicles") List<Realm> realms) {

        // Collect all available vehicle IDs
        Set<String> allAvailableVehicleIds = realms.stream()
                .flatMap(realm -> realm.getVehicles().stream())
                .map(Vehicle::getId)
                .collect(Collectors.toSet());

        // Assume at least one vehicle is available
        Assume.that(!allAvailableVehicleIds.isEmpty());

        // Configure mocks
        when(discoveryService.getCachedRealms()).thenReturn(realms);
        when(discoveryService.getCachedVehicles(anyString())).thenReturn(Collections.emptyList());

        // Execute
        orchestrator.startRandomMode();

        // Verify: active sessions should exist (non-empty selection)
        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();
        assertFalse(sessions.isEmpty(),
                "Random Mode should select at least one vehicle, but no sessions were created");

        // Verify: all selected vehicles are from the available set
        Set<String> selectedVehicleIds = sessions.values().stream()
                .map(session -> session.getVehicle().getId())
                .collect(Collectors.toSet());

        assertTrue(allAvailableVehicleIds.containsAll(selectedVehicleIds),
                "All selected vehicles must be from the available vehicles. " +
                        "Selected: " + selectedVehicleIds + ", Available: " + allAvailableVehicleIds);

        // Verify: selection is non-empty (at least 1 vehicle selected)
        assertFalse(selectedVehicleIds.isEmpty(),
                "At least one vehicle must be selected in Random Mode");

        // Verify: selection size does not exceed available vehicles
        assertTrue(selectedVehicleIds.size() <= allAvailableVehicleIds.size(),
                "Selected vehicles count (" + selectedVehicleIds.size() +
                        ") should not exceed available vehicles count (" + allAvailableVehicleIds.size() + ")");

        // Cleanup
        orchestrator.stopRandomMode();
    }

    /**
     * For any available vehicles, starting Random Mode multiple times should
     * not always select the same vehicles (demonstrating randomness).
     * We run startRandomMode several times and check that selections vary.
     *
     * <p><b>Validates: Requirements 4.1</b></p>
     */
    @Property(tries = 50)
    void multipleRandomSelections_produceVariedResults(
            @ForAll("realmsWithManyVehicles") List<Realm> realms) {

        Set<String> allAvailableVehicleIds = realms.stream()
                .flatMap(realm -> realm.getVehicles().stream())
                .map(Vehicle::getId)
                .collect(Collectors.toSet());

        // Need at least 3 vehicles for meaningful randomness check
        Assume.that(allAvailableVehicleIds.size() >= 3);

        when(discoveryService.getCachedRealms()).thenReturn(realms);
        when(discoveryService.getCachedVehicles(anyString())).thenReturn(Collections.emptyList());

        Set<Set<String>> distinctSelections = new HashSet<>();

        // Try 10 times to observe different selections
        for (int i = 0; i < 10; i++) {
            orchestrator.startRandomMode();

            Set<String> selectedVehicleIds = orchestrator.getActiveSessions().values().stream()
                    .map(session -> session.getVehicle().getId())
                    .collect(Collectors.toSet());

            distinctSelections.add(selectedVehicleIds);

            orchestrator.stopRandomMode();
        }

        // With at least 3 vehicles, running 10 random selections should produce
        // at least 2 distinct outcomes (very high probability)
        assertTrue(distinctSelections.size() >= 2,
                "Multiple random selections should produce varied results. " +
                        "Got only " + distinctSelections.size() + " distinct selection(s) from 10 runs " +
                        "with " + allAvailableVehicleIds.size() + " available vehicles");
    }

    /**
     * For any set of realms with vehicles, the selected vehicles should span
     * across realms (not always from a single realm) when multiple realms
     * have vehicles. We verify that over multiple runs, vehicles from different
     * realms are selected.
     *
     * <p><b>Validates: Requirements 4.1</b></p>
     */
    @Property(tries = 50)
    void randomSelection_spansAcrossRealms(
            @ForAll("multipleRealmsWithVehicles") List<Realm> realms) {

        // Need at least 2 realms each with at least 2 vehicles
        long realmsWithVehicles = realms.stream()
                .filter(r -> r.getVehicles() != null && r.getVehicles().size() >= 2)
                .count();
        Assume.that(realmsWithVehicles >= 2);

        when(discoveryService.getCachedRealms()).thenReturn(realms);
        when(discoveryService.getCachedVehicles(anyString())).thenReturn(Collections.emptyList());

        Set<String> realmsRepresented = new HashSet<>();

        // Run multiple times to collect which realms get represented
        for (int i = 0; i < 20; i++) {
            orchestrator.startRandomMode();

            orchestrator.getActiveSessions().values().stream()
                    .map(session -> session.getVehicle().getRealmId())
                    .forEach(realmsRepresented::add);

            orchestrator.stopRandomMode();
        }

        // Over 20 random selections with multiple realms, we expect vehicles
        // from more than one realm to be selected at some point
        assertTrue(realmsRepresented.size() >= 2,
                "Random selection should span across multiple realms over many runs. " +
                        "Only realms represented: " + realmsRepresented);
    }

    // --- Providers ---

    /**
     * Generates 1-5 realms, each containing 1-10 vehicles.
     */
    @Provide
    Arbitrary<List<Realm>> realmsWithVehicles() {
        return Arbitraries.integers().between(1, 5).flatMap(realmCount -> {
            List<Arbitrary<Realm>> realmArbitraries = new ArrayList<>();
            for (int i = 0; i < realmCount; i++) {
                int realmIdx = i;
                realmArbitraries.add(
                        Arbitraries.integers().between(1, 10).map(vehicleCount -> {
                            String realmId = "realm-" + realmIdx;
                            String realmName = "Realm " + realmIdx;
                            List<Vehicle> vehicles = new ArrayList<>();
                            for (int v = 0; v < vehicleCount; v++) {
                                String vehicleId = realmId + "-veh-" + v;
                                vehicles.add(new Vehicle(vehicleId, "Vehicle " + v,
                                        realmId, VehicleStatus.ACTIVE));
                            }
                            return new Realm(realmId, realmName, vehicles);
                        })
                );
            }
            return Combinators.combine(realmArbitraries).as(realms -> realms);
        });
    }

    /**
     * Generates 1-3 realms, each containing 5-15 vehicles to ensure enough
     * vehicles for meaningful randomness testing.
     */
    @Provide
    Arbitrary<List<Realm>> realmsWithManyVehicles() {
        return Arbitraries.integers().between(1, 3).flatMap(realmCount -> {
            List<Arbitrary<Realm>> realmArbitraries = new ArrayList<>();
            for (int i = 0; i < realmCount; i++) {
                int realmIdx = i;
                realmArbitraries.add(
                        Arbitraries.integers().between(5, 15).map(vehicleCount -> {
                            String realmId = "realm-" + realmIdx;
                            String realmName = "Realm " + realmIdx;
                            List<Vehicle> vehicles = new ArrayList<>();
                            for (int v = 0; v < vehicleCount; v++) {
                                String vehicleId = realmId + "-veh-" + v;
                                vehicles.add(new Vehicle(vehicleId, "Vehicle " + v,
                                        realmId, VehicleStatus.ACTIVE));
                            }
                            return new Realm(realmId, realmName, vehicles);
                        })
                );
            }
            return Combinators.combine(realmArbitraries).as(realms -> realms);
        });
    }

    /**
     * Generates 2-4 realms, each containing 2-8 vehicles, specifically
     * to test cross-realm selection.
     */
    @Provide
    Arbitrary<List<Realm>> multipleRealmsWithVehicles() {
        return Arbitraries.integers().between(2, 4).flatMap(realmCount -> {
            List<Arbitrary<Realm>> realmArbitraries = new ArrayList<>();
            for (int i = 0; i < realmCount; i++) {
                int realmIdx = i;
                realmArbitraries.add(
                        Arbitraries.integers().between(2, 8).map(vehicleCount -> {
                            String realmId = "realm-" + realmIdx;
                            String realmName = "Realm " + realmIdx;
                            List<Vehicle> vehicles = new ArrayList<>();
                            for (int v = 0; v < vehicleCount; v++) {
                                String vehicleId = realmId + "-veh-" + v;
                                vehicles.add(new Vehicle(vehicleId, "Vehicle " + v,
                                        realmId, VehicleStatus.ACTIVE));
                            }
                            return new Realm(realmId, realmName, vehicles);
                        })
                );
            }
            return Combinators.combine(realmArbitraries).as(realms -> realms);
        });
    }
}
