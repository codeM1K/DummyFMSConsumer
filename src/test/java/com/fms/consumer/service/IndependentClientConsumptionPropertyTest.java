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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for independent client data consumption
 * in {@link ConsumptionOrchestrator}.
 *
 * <p><b>Validates: Requirements 7.4</b></p>
 *
 * <p>Property 20: Independent Client Data Consumption —
 * "For any multi-client configuration with active subscriptions, each simulated client
 * SHALL consume Location_Data independently without interference from other clients."</p>
 */
class IndependentClientConsumptionPropertyTest {

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

        metricsCollector.reset();

        orchestrator = new ConsumptionOrchestrator(clientPool, locationPollingService, metricsCollector, discoveryService, configService);
    }

    /**
     * For any multi-client configuration, each client's session SHALL have its own
     * unique session key combining vehicleId and clientId. This ensures each client
     * consumes data independently.
     *
     * <p><b>Validates: Requirements 7.4</b></p>
     */
    @Property(tries = 100)
    void multiClient_eachClientHasIndependentSessionKey(
            @ForAll("vehicleSets") Set<Vehicle> vehicles,
            @ForAll("clientCounts") int clientCount) {

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(vehicles);

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        // Total sessions should equal vehicles * clients
        assertEquals(vehicles.size() * clientCount, sessions.size(),
                "Expected " + (vehicles.size() * clientCount) + " sessions " +
                        "for " + vehicles.size() + " vehicles and " + clientCount + " clients, " +
                        "but got " + sessions.size());

        // Each session key must be unique and follow {vehicleId}_{clientId} format
        for (Vehicle vehicle : vehicles) {
            for (int i = 1; i <= clientCount; i++) {
                String expectedKey = vehicle.getId() + "_client_" + i;
                assertTrue(sessions.containsKey(expectedKey),
                        "Expected session key '" + expectedKey + "' not found. " +
                                "Available keys: " + sessions.keySet());
            }
        }
    }

    /**
     * For any multi-client configuration, stopping one client's sessions for specific
     * vehicles SHALL NOT affect other clients' sessions for the same or other vehicles.
     *
     * <p><b>Validates: Requirements 7.4</b></p>
     */
    @Property(tries = 100)
    void multiClient_stoppingVehiclesForOneClientDoesNotAffectOthers(
            @ForAll("vehicleSetsWithAtLeastTwo") List<Vehicle> vehicleList,
            @ForAll("clientCounts") int clientCount) {

        Set<Vehicle> allVehicles = new LinkedHashSet<>(vehicleList);

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(allVehicles);

        // Pick a subset of vehicles to stop (first half)
        List<Vehicle> vehiclesToStop = vehicleList.subList(0, vehicleList.size() / 2);
        Set<Vehicle> stopSet = new HashSet<>(vehiclesToStop);

        // Remaining vehicles should keep their sessions
        Set<Vehicle> remainingVehicles = new HashSet<>(allVehicles);
        remainingVehicles.removeAll(stopSet);

        int sessionsBeforeStop = orchestrator.getActiveSessions().size();

        // Stop controlled mode for the subset of vehicles
        orchestrator.stopControlledMode(stopSet);

        Map<String, ConsumptionSession> sessionsAfterStop = orchestrator.getActiveSessions();

        // Sessions for remaining vehicles must still exist for ALL clients
        for (Vehicle remainingVehicle : remainingVehicles) {
            for (int i = 1; i <= clientCount; i++) {
                String expectedKey = remainingVehicle.getId() + "_client_" + i;
                assertTrue(sessionsAfterStop.containsKey(expectedKey),
                        "Session '" + expectedKey + "' for remaining vehicle should still exist " +
                                "after stopping other vehicles. Available: " + sessionsAfterStop.keySet());

                ConsumptionSession session = sessionsAfterStop.get(expectedKey);
                assertTrue(session.isActive(),
                        "Session '" + expectedKey + "' should still be active");
            }
        }

        // Sessions for stopped vehicles must be removed for ALL clients
        for (Vehicle stoppedVehicle : stopSet) {
            for (int i = 1; i <= clientCount; i++) {
                String stoppedKey = stoppedVehicle.getId() + "_client_" + i;
                assertFalse(sessionsAfterStop.containsKey(stoppedKey),
                        "Session '" + stoppedKey + "' for stopped vehicle should be removed");
            }
        }

        // Verify the remaining count
        int expectedRemaining = remainingVehicles.size() * clientCount;
        assertEquals(expectedRemaining, sessionsAfterStop.size(),
                "Expected " + expectedRemaining + " remaining sessions but got " +
                        sessionsAfterStop.size());
    }

    /**
     * For any multi-client configuration, each client's session SHALL have a distinct
     * clientId, ensuring data consumption independence between clients for the same vehicle.
     *
     * <p><b>Validates: Requirements 7.4</b></p>
     */
    @Property(tries = 100)
    void multiClient_eachSessionHasDistinctClientId(
            @ForAll("vehicleSets") Set<Vehicle> vehicles,
            @ForAll("clientCounts") int clientCount) {

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(vehicles);

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        // For each vehicle, all client sessions must have distinct clientIds
        for (Vehicle vehicle : vehicles) {
            Set<String> clientIdsForVehicle = sessions.values().stream()
                    .filter(s -> s.getVehicle().getId().equals(vehicle.getId()))
                    .map(ConsumptionSession::getClientId)
                    .collect(Collectors.toSet());

            assertEquals(clientCount, clientIdsForVehicle.size(),
                    "Vehicle " + vehicle.getId() + " should have " + clientCount +
                            " distinct client IDs but has: " + clientIdsForVehicle);
        }
    }

    /**
     * For any multi-client configuration, a WebSocket connection SHALL be created
     * independently for each client-vehicle pair, demonstrating independent consumption paths.
     *
     * <p><b>Validates: Requirements 7.4</b></p>
     */
    @Property(tries = 100)
    void multiClient_independentPollingSubscriptionPerClientVehiclePair(
            @ForAll("vehicleSets") Set<Vehicle> vehicles,
            @ForAll("clientCounts") int clientCount) {

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(vehicles);

        // Verify each vehicle-client pair got its own subscribeVehicle call
        for (Vehicle vehicle : vehicles) {
            for (int i = 1; i <= clientCount; i++) {
                verify(locationPollingService).subscribeVehicle(eq(vehicle.getId()), eq("client_" + i));
            }
        }

        // Total subscription creations should be vehicles * clients
        verify(locationPollingService, times(vehicles.size() * clientCount))
                .subscribeVehicle(anyString(), anyString());
    }

    // --- Generators ---

    /**
     * Generates non-empty sets of vehicles with unique IDs (1 to 6 vehicles).
     */
    @Provide
    Arbitrary<Set<Vehicle>> vehicleSets() {
        Arbitrary<Vehicle> vehicleArbitrary = Combinators.combine(
                vehicleIds(),
                vehicleNames(),
                realmIds()
        ).as((id, name, realmId) -> new Vehicle(id, name, realmId, VehicleStatus.ACTIVE));

        return vehicleArbitrary.set().ofMinSize(1).ofMaxSize(6);
    }

    /**
     * Generates lists of vehicles with at least 2 unique vehicles (for stop/continue tests).
     */
    @Provide
    Arbitrary<List<Vehicle>> vehicleSetsWithAtLeastTwo() {
        Arbitrary<Vehicle> vehicleArbitrary = Combinators.combine(
                vehicleIds(),
                vehicleNames(),
                realmIds()
        ).as((id, name, realmId) -> new Vehicle(id, name, realmId, VehicleStatus.ACTIVE));

        return vehicleArbitrary.list().ofMinSize(2).ofMaxSize(6)
                .filter(list -> {
                    // Ensure unique vehicle IDs
                    Set<String> ids = list.stream().map(Vehicle::getId).collect(Collectors.toSet());
                    return ids.size() == list.size();
                });
    }

    /**
     * Generates client counts between 2 and 5 to test multi-client scenarios.
     */
    @Provide
    Arbitrary<Integer> clientCounts() {
        return Arbitraries.integers().between(2, 5);
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
