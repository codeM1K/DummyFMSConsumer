package com.fms.consumer.service;

import com.fms.consumer.integration.WebSocketClientPool;
import com.fms.consumer.model.ConsumptionMode;
import com.fms.consumer.model.ConsumptionSession;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.model.VehicleStatus;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for isolated vehicle failure handling
 * in {@link ConsumptionOrchestrator}.
 *
 * <p><b>Validates: Requirements 12.5</b></p>
 *
 * <p>Property 37: Isolated Vehicle Failure Handling —
 * "For any vehicle in a set of actively consumed vehicles, if consumption fails
 * for that specific vehicle, the system SHALL continue consuming data from all
 * other vehicles without interruption."</p>
 */
class IsolatedVehicleFailurePropertyTest {

    private WebSocketClientPool clientPool; private LocationPollingService locationPollingService;
    private MetricsCollector metricsCollector;
    private DiscoveryService discoveryService;
    private ConfigurationService configService;
    private ConsumptionOrchestrator orchestrator;

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
    void setUpTry() {
        clientPool = mock(WebSocketClientPool.class); locationPollingService = mock(LocationPollingService.class);
        discoveryService = mock(DiscoveryService.class);
        configService = mock(ConfigurationService.class);

        when(configService.getSimulationMaxClients()).thenReturn(100);
        when(configService.getRetryInitialDelay()).thenReturn(1000);
        when(configService.getRetryMaxDelay()).thenReturn(30000);
        when(configService.getRetryMaxAttempts()).thenReturn(3);

        metricsCollector.reset();

        orchestrator = new ConsumptionOrchestrator(clientPool, locationPollingService, metricsCollector, discoveryService, configService);
    }

    /**
     * For any set of actively consumed vehicles, when handleVehicleFailure() is called
     * for one vehicle, all other vehicles' sessions remain in the active sessions map
     * and are unaffected.
     *
     * <p><b>Validates: Requirements 12.5</b></p>
     */
    @Property(tries = 100)
    void failureForOneVehicle_otherVehicleSessionsRemainActive(
            @ForAll("vehicleListsWithAtLeastTwo") List<Vehicle> vehicleList) {

        Set<Vehicle> allVehicles = new LinkedHashSet<>(vehicleList);
        String clientId = "client_1";

        // Start controlled mode for all vehicles
        orchestrator.startControlledMode(allVehicles);

        // Pick the first vehicle as the one that fails
        Vehicle failedVehicle = vehicleList.get(0);
        Set<Vehicle> remainingVehicles = new LinkedHashSet<>(allVehicles);
        remainingVehicles.remove(failedVehicle);

        // Trigger failure for the selected vehicle
        orchestrator.handleVehicleFailure(
                failedVehicle.getId(), clientId, new RuntimeException("Connection lost"));

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        // All other vehicles' sessions must still be present and active
        for (Vehicle vehicle : remainingVehicles) {
            String sessionKey = vehicle.getId() + "_" + clientId;
            assertTrue(sessions.containsKey(sessionKey),
                    "Session for vehicle '" + vehicle.getId() + "' should still exist " +
                            "after failure of vehicle '" + failedVehicle.getId() + "'. " +
                            "Available keys: " + sessions.keySet());

            ConsumptionSession session = sessions.get(sessionKey);
            assertTrue(session.isActive(),
                    "Session for vehicle '" + vehicle.getId() + "' should remain active " +
                            "after failure of vehicle '" + failedVehicle.getId() + "'");
        }
    }

    /**
     * For any set of actively consumed vehicles, when handleVehicleFailure() is called
     * for one vehicle, the failed vehicle's session is deactivated (marked inactive)
     * but the session key remains in the map for retry purposes.
     *
     * <p><b>Validates: Requirements 12.5</b></p>
     */
    @Property(tries = 100)
    void failureForOneVehicle_failedSessionIsDeactivated(
            @ForAll("vehicleListsWithAtLeastTwo") List<Vehicle> vehicleList) {

        Set<Vehicle> allVehicles = new LinkedHashSet<>(vehicleList);
        String clientId = "client_1";

        orchestrator.startControlledMode(allVehicles);

        Vehicle failedVehicle = vehicleList.get(0);

        // Trigger failure
        orchestrator.handleVehicleFailure(
                failedVehicle.getId(), clientId, new RuntimeException("Simulated failure"));

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();
        String failedKey = failedVehicle.getId() + "_" + clientId;

        // The failed session should still be in the map (kept for retry)
        assertTrue(sessions.containsKey(failedKey),
                "Failed session key '" + failedKey + "' should remain in active sessions for retry");

        // The failed session should be deactivated
        ConsumptionSession failedSession = sessions.get(failedKey);
        assertFalse(failedSession.isActive(),
                "Failed session for vehicle '" + failedVehicle.getId() + "' should be deactivated");
    }

    /**
     * For any set of actively consumed vehicles across multiple clients, when
     * handleVehicleFailure() is called for one vehicle-client pair, all other
     * vehicle-client pair sessions remain active and unaffected.
     *
     * <p><b>Validates: Requirements 12.5</b></p>
     */
    @Property(tries = 100)
    void failureInMultiClient_otherClientSessionsUnaffected(
            @ForAll("vehicleListsWithAtLeastTwo") List<Vehicle> vehicleList,
            @ForAll("clientCounts") int clientCount) {

        Set<Vehicle> allVehicles = new LinkedHashSet<>(vehicleList);

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(allVehicles);

        // Fail one specific vehicle-client pair
        Vehicle failedVehicle = vehicleList.get(0);
        String failedClientId = "client_1";

        orchestrator.handleVehicleFailure(
                failedVehicle.getId(), failedClientId, new RuntimeException("Connection timeout"));

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        // All other sessions (different vehicle OR different client) must remain active
        for (Vehicle vehicle : allVehicles) {
            for (int i = 1; i <= clientCount; i++) {
                String currentClientId = "client_" + i;
                String sessionKey = vehicle.getId() + "_" + currentClientId;

                if (vehicle.getId().equals(failedVehicle.getId())
                        && currentClientId.equals(failedClientId)) {
                    // This is the failed session - should be deactivated
                    assertTrue(sessions.containsKey(sessionKey),
                            "Failed session should still be in map for retry");
                    assertFalse(sessions.get(sessionKey).isActive(),
                            "Failed session should be deactivated");
                } else {
                    // All other sessions should be active and unaffected
                    assertTrue(sessions.containsKey(sessionKey),
                            "Session '" + sessionKey + "' should still exist after failure of " +
                                    failedVehicle.getId() + "_" + failedClientId);
                    assertTrue(sessions.get(sessionKey).isActive(),
                            "Session '" + sessionKey + "' should still be active after failure of " +
                                    failedVehicle.getId() + "_" + failedClientId);
                }
            }
        }
    }

    /**
     * For any vehicle in a set of actively consumed vehicles, multiple sequential
     * failures for different vehicles SHALL each be isolated - the remaining
     * vehicles continue without interruption.
     *
     * <p><b>Validates: Requirements 12.5</b></p>
     */
    @Property(tries = 100)
    void multipleSequentialFailures_remainingVehiclesContinue(
            @ForAll("vehicleListsWithAtLeastThree") List<Vehicle> vehicleList) {

        Set<Vehicle> allVehicles = new LinkedHashSet<>(vehicleList);
        String clientId = "client_1";

        orchestrator.startControlledMode(allVehicles);

        // Fail first two vehicles sequentially
        Vehicle failedVehicle1 = vehicleList.get(0);
        Vehicle failedVehicle2 = vehicleList.get(1);

        orchestrator.handleVehicleFailure(
                failedVehicle1.getId(), clientId, new RuntimeException("Failure 1"));
        orchestrator.handleVehicleFailure(
                failedVehicle2.getId(), clientId, new RuntimeException("Failure 2"));

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        // All remaining vehicles (those not failed) should still be active
        Set<Vehicle> stillActiveVehicles = new LinkedHashSet<>(allVehicles);
        stillActiveVehicles.remove(failedVehicle1);
        stillActiveVehicles.remove(failedVehicle2);

        for (Vehicle vehicle : stillActiveVehicles) {
            String sessionKey = vehicle.getId() + "_" + clientId;
            assertTrue(sessions.containsKey(sessionKey),
                    "Session for vehicle '" + vehicle.getId() + "' should still exist " +
                            "after multiple other failures");
            assertTrue(sessions.get(sessionKey).isActive(),
                    "Session for vehicle '" + vehicle.getId() + "' should still be active " +
                            "after multiple other failures");
        }

        // Total sessions in map should still equal the original count
        // (failed sessions are kept for retry)
        assertEquals(allVehicles.size(), sessions.size(),
                "Total session count should remain unchanged (failed sessions kept for retry)");
    }

    // --- Generators ---

    /**
     * Generates lists of vehicles with at least 2 unique vehicles.
     */
    @Provide
    Arbitrary<List<Vehicle>> vehicleListsWithAtLeastTwo() {
        Arbitrary<Vehicle> vehicleArbitrary = Combinators.combine(
                vehicleIds(),
                vehicleNames(),
                realmIds()
        ).as((id, name, realmId) -> new Vehicle(id, name, realmId, VehicleStatus.ACTIVE));

        return vehicleArbitrary.list().ofMinSize(2).ofMaxSize(6)
                .filter(list -> {
                    Set<String> ids = list.stream().map(Vehicle::getId).collect(Collectors.toSet());
                    return ids.size() == list.size();
                });
    }

    /**
     * Generates lists of vehicles with at least 3 unique vehicles
     * (for sequential failure tests).
     */
    @Provide
    Arbitrary<List<Vehicle>> vehicleListsWithAtLeastThree() {
        Arbitrary<Vehicle> vehicleArbitrary = Combinators.combine(
                vehicleIds(),
                vehicleNames(),
                realmIds()
        ).as((id, name, realmId) -> new Vehicle(id, name, realmId, VehicleStatus.ACTIVE));

        return vehicleArbitrary.list().ofMinSize(3).ofMaxSize(6)
                .filter(list -> {
                    Set<String> ids = list.stream().map(Vehicle::getId).collect(Collectors.toSet());
                    return ids.size() == list.size();
                });
    }

    /**
     * Generates client counts between 2 and 4 for multi-client tests.
     */
    @Provide
    Arbitrary<Integer> clientCounts() {
        return Arbitraries.integers().between(2, 4);
    }

    private Arbitrary<String> vehicleIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(4)
                .ofMaxLength(10)
                .map(s -> "v-" + s);
    }

    private Arbitrary<String> vehicleNames() {
        return Arbitraries.of(
                "Fleet Truck 1", "Fleet Truck 2", "Fleet Truck 3",
                "City Bus Alpha", "City Bus Beta", "City Bus Gamma",
                "Delivery Van A", "Delivery Van B",
                "Heavy Loader X", "Light Cargo Y"
        );
    }

    private Arbitrary<String> realmIds() {
        return Arbitraries.of(
                "realm-1", "realm-2", "realm-3",
                "production", "staging",
                "fleet-management", "eu-west-1"
        );
    }
}
