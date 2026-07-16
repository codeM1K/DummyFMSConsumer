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
 * Property-based tests for cascading realm selection in {@link ConsumptionOrchestrator}.
 *
 * <p><b>Validates: Requirements 5.3</b></p>
 *
 * <p>Property 12: Cascading Realm Selection —
 * "For any realm selection by the user, the system SHALL automatically select
 * all vehicles within that realm."</p>
 *
 * <p>This test validates that when a user selects all vehicles from a realm and
 * passes them to startControlledMode(), all vehicles in that realm get active sessions.
 * This models the cascading behavior where realm selection = all vehicles in that realm
 * get consumption sessions.</p>
 */
class CascadingRealmSelectionPropertyTest {

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
     * For any realm with vehicles, when all vehicles from that realm are passed
     * to startControlledMode(), every vehicle in that realm SHALL have an active session.
     *
     * <p><b>Validates: Requirements 5.3</b></p>
     */
    @Property(tries = 200)
    void selectingAllVehiclesFromRealm_createsSessionForEveryVehicle(
            @ForAll("realmsWithVehicles") List<Realm> realms) {

        // Pick one realm to "select"
        Assume.that(!realms.isEmpty());
        Realm selectedRealm = realms.get(0);
        Assume.that(selectedRealm.getVehicles() != null && !selectedRealm.getVehicles().isEmpty());

        // Cascading realm selection: all vehicles in the realm are selected
        Set<Vehicle> realmVehicles = new HashSet<>(selectedRealm.getVehicles());

        // Execute controlled mode with all vehicles from the realm
        orchestrator.startControlledMode(realmVehicles);

        // Verify: every vehicle in the realm has an active session
        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();
        Set<String> activeVehicleIds = sessions.values().stream()
                .map(session -> session.getVehicle().getId())
                .collect(Collectors.toSet());

        Set<String> expectedVehicleIds = realmVehicles.stream()
                .map(Vehicle::getId)
                .collect(Collectors.toSet());

        assertEquals(expectedVehicleIds, activeVehicleIds,
                "All vehicles from the selected realm must have active sessions. " +
                        "Expected: " + expectedVehicleIds + ", Active: " + activeVehicleIds);

        // Cleanup
        orchestrator.stopControlledMode(realmVehicles);
    }

    /**
     * For any realm with vehicles, when all vehicles from that realm are passed
     * to startControlledMode(), the number of active sessions SHALL equal the
     * number of vehicles in the realm (times the client count, which defaults to 1).
     *
     * <p><b>Validates: Requirements 5.3</b></p>
     */
    @Property(tries = 200)
    void realmSelection_createsExactlyOneSessionPerVehicle(
            @ForAll("realmsWithVehicles") List<Realm> realms) {

        Assume.that(!realms.isEmpty());
        Realm selectedRealm = realms.get(0);
        Assume.that(selectedRealm.getVehicles() != null && !selectedRealm.getVehicles().isEmpty());

        Set<Vehicle> realmVehicles = new HashSet<>(selectedRealm.getVehicles());

        orchestrator.startControlledMode(realmVehicles);

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        // With default client count of 1, session count should equal vehicle count
        assertEquals(realmVehicles.size(), sessions.size(),
                "Session count should equal the number of vehicles in the realm. " +
                        "Realm vehicles: " + realmVehicles.size() + ", Sessions: " + sessions.size());

        // Cleanup
        orchestrator.stopControlledMode(realmVehicles);
    }

    /**
     * For any set of realms, when all vehicles from multiple realms are selected
     * (cascading selection on each realm), all vehicles across all selected realms
     * SHALL have active sessions.
     *
     * <p><b>Validates: Requirements 5.3</b></p>
     */
    @Property(tries = 200)
    void selectingMultipleRealms_createsSessionsForAllVehiclesAcrossRealms(
            @ForAll("multipleRealmsWithVehicles") List<Realm> realms) {

        Assume.that(realms.size() >= 2);

        // Cascading selection: selecting multiple realms means all vehicles in each realm
        Set<Vehicle> allSelectedVehicles = realms.stream()
                .flatMap(realm -> realm.getVehicles().stream())
                .collect(Collectors.toSet());

        Assume.that(!allSelectedVehicles.isEmpty());

        orchestrator.startControlledMode(allSelectedVehicles);

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();
        Set<String> activeVehicleIds = sessions.values().stream()
                .map(session -> session.getVehicle().getId())
                .collect(Collectors.toSet());

        Set<String> expectedVehicleIds = allSelectedVehicles.stream()
                .map(Vehicle::getId)
                .collect(Collectors.toSet());

        assertEquals(expectedVehicleIds, activeVehicleIds,
                "Selecting multiple realms should create sessions for ALL vehicles in those realms. " +
                        "Expected " + expectedVehicleIds.size() + " vehicles, got " + activeVehicleIds.size());

        // Cleanup
        orchestrator.stopControlledMode(allSelectedVehicles);
    }

    /**
     * For any realm, all sessions created by cascading realm selection SHALL be
     * in CONTROLLED mode.
     *
     * <p><b>Validates: Requirements 5.3</b></p>
     */
    @Property(tries = 200)
    void cascadingRealmSelection_allSessionsAreControlledMode(
            @ForAll("realmsWithVehicles") List<Realm> realms) {

        Assume.that(!realms.isEmpty());
        Realm selectedRealm = realms.get(0);
        Assume.that(selectedRealm.getVehicles() != null && !selectedRealm.getVehicles().isEmpty());

        Set<Vehicle> realmVehicles = new HashSet<>(selectedRealm.getVehicles());

        orchestrator.startControlledMode(realmVehicles);

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        boolean allControlled = sessions.values().stream()
                .allMatch(session -> session.getMode() == ConsumptionMode.CONTROLLED);

        assertTrue(allControlled,
                "All sessions from cascading realm selection must be in CONTROLLED mode");

        // Cleanup
        orchestrator.stopControlledMode(realmVehicles);
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
     * Generates 2-4 realms, each containing 2-8 vehicles.
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
