package com.fms.consumer.service;

import com.fms.consumer.integration.WebSocketClientPool;
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
 * Property-based tests for vehicle distribution across simulated clients
 * in {@link ConsumptionOrchestrator}.
 *
 * <p><b>Validates: Requirements 7.3</b></p>
 *
 * <p>Property 19: Vehicle Distribution Across Clients —
 * "For any set of vehicles and configured number of clients, the system SHALL
 * distribute vehicle subscriptions across all simulated clients."</p>
 *
 * <p>The current implementation creates sessions for each vehicle × each client,
 * meaning every client gets connections to all vehicles.</p>
 */
class VehicleDistributionPropertyTest {

    private WebSocketClientPool clientPool;
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
        clientPool = mock(WebSocketClientPool.class);
        discoveryService = mock(DiscoveryService.class);
        configService = mock(ConfigurationService.class);

        when(configService.getSimulationMaxClients()).thenReturn(100);

        metricsCollector.reset();

        orchestrator = new ConsumptionOrchestrator(
                clientPool, metricsCollector, discoveryService, configService);
    }

    /**
     * For any configured number of clients and set of vehicles, when multi-client
     * mode is active, each client ID SHALL appear in the active sessions.
     *
     * <p><b>Validates: Requirements 7.3</b></p>
     */
    @Property(tries = 100)
    void eachClientId_appearsInActiveSessions(
            @ForAll("clientCounts") int clientCount,
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(vehicles);

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        // Collect all client IDs present in sessions
        Set<String> sessionClientIds = sessions.values().stream()
                .map(ConsumptionSession::getClientId)
                .collect(Collectors.toSet());

        // Every configured client ID must be present
        for (int i = 1; i <= clientCount; i++) {
            String expectedClientId = "client_" + i;
            assertTrue(sessionClientIds.contains(expectedClientId),
                    "Client '" + expectedClientId + "' must appear in sessions. " +
                            "Found clients: " + sessionClientIds);
        }
    }

    /**
     * For any configured number of clients and set of vehicles, each client SHALL
     * have connections to ALL vehicles (since the implementation creates sessions
     * for each vehicle × each client).
     *
     * <p><b>Validates: Requirements 7.3</b></p>
     */
    @Property(tries = 100)
    void eachClient_hasConnectionsToAllVehicles(
            @ForAll("clientCounts") int clientCount,
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(vehicles);

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        Set<String> expectedVehicleIds = vehicles.stream()
                .map(Vehicle::getId)
                .collect(Collectors.toSet());

        // For each client, verify it has sessions for all vehicles
        for (int i = 1; i <= clientCount; i++) {
            String clientId = "client_" + i;

            Set<String> vehicleIdsForClient = sessions.values().stream()
                    .filter(s -> s.getClientId().equals(clientId))
                    .map(s -> s.getVehicle().getId())
                    .collect(Collectors.toSet());

            assertEquals(expectedVehicleIds, vehicleIdsForClient,
                    "Client '" + clientId + "' must have sessions for all vehicles. " +
                            "Expected: " + expectedVehicleIds + ", got: " + vehicleIdsForClient);
        }
    }

    /**
     * For any configured number of clients and set of vehicles, the total number
     * of sessions SHALL equal vehicles.size() × clientCount (full distribution).
     *
     * <p><b>Validates: Requirements 7.3</b></p>
     */
    @Property(tries = 100)
    void totalSessions_equalsVehiclesTimesClients(
            @ForAll("clientCounts") int clientCount,
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(vehicles);

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        int expectedSessionCount = vehicles.size() * clientCount;
        assertEquals(expectedSessionCount, sessions.size(),
                "Total sessions must be vehicles × clients. " +
                        "Vehicles=" + vehicles.size() + ", clients=" + clientCount +
                        ", expected=" + expectedSessionCount + ", actual=" + sessions.size());
    }

    // --- Generators ---

    /**
     * Generates client counts between 1 and 10 for multi-client mode.
     */
    @Provide
    Arbitrary<Integer> clientCounts() {
        return Arbitraries.integers().between(1, 10);
    }

    /**
     * Generates non-empty sets of vehicles with unique IDs (1 to 8 vehicles).
     */
    @Provide
    Arbitrary<Set<Vehicle>> vehicleSets() {
        Arbitrary<Vehicle> vehicleArbitrary = Combinators.combine(
                vehicleIds(),
                vehicleNames(),
                realmIds()
        ).as((id, name, realmId) -> new Vehicle(id, name, realmId, VehicleStatus.ACTIVE));

        return vehicleArbitrary.set().ofMinSize(1).ofMaxSize(8);
    }

    private Arbitrary<String> vehicleIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(4)
                .ofMaxLength(12)
                .map(s -> "v-" + s);
    }

    private Arbitrary<String> vehicleNames() {
        return Arbitraries.of(
                "Fleet Truck 1", "Fleet Truck 2", "Fleet Truck 3",
                "City Bus Alpha", "City Bus Beta", "City Bus Gamma",
                "Delivery Van A", "Delivery Van B",
                "Heavy Loader X", "Light Cargo Y",
                "Emergency Vehicle", "Transport Unit 42",
                "Patrol Car", "Service Van", "Tanker"
        );
    }

    private Arbitrary<String> realmIds() {
        return Arbitraries.of(
                "realm-1", "realm-2", "realm-3",
                "production", "staging",
                "fleet-management", "eu-west-1",
                "alamanos", "master"
        );
    }
}
