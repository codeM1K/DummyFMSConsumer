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
 * Property-based tests for location data consumption in Controlled Mode
 * in {@link ConsumptionOrchestrator}.
 *
 * <p><b>Validates: Requirements 5.5</b></p>
 *
 * <p>Property 13: Location Data Consumption in Controlled Mode —
 * "For any user-selected vehicle with an established WebSocket connection in Controlled Mode,
 * the system SHALL consume and process Location_Data updates in real-time."</p>
 */
class ControlledModeConsumptionPropertyTest {

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
     * For any non-empty set of vehicles, starting Controlled Mode SHALL create
     * an active consumption session for each vehicle.
     *
     * <p><b>Validates: Requirements 5.5</b></p>
     */
    @Property(tries = 100)
    void startControlledMode_createsActiveSessionForEachVehicle(
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.startControlledMode(vehicles);

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        // Each vehicle should have exactly one session (with default client count of 1)
        assertEquals(vehicles.size(), sessions.size(),
                "Expected one session per vehicle. Vehicles: " + vehicles.size() +
                        ", sessions: " + sessions.size());

        // All sessions must be active
        for (ConsumptionSession session : sessions.values()) {
            assertTrue(session.isActive(),
                    "Session for vehicle " + session.getVehicle().getId() + " must be active");
        }
    }

    /**
     * For any set of vehicles started in Controlled Mode, every session
     * SHALL be in CONTROLLED mode.
     *
     * <p><b>Validates: Requirements 5.5</b></p>
     */
    @Property(tries = 100)
    void startControlledMode_allSessionsInControlledMode(
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.startControlledMode(vehicles);

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        for (ConsumptionSession session : sessions.values()) {
            assertEquals(ConsumptionMode.CONTROLLED, session.getMode(),
                    "Session for vehicle " + session.getVehicle().getId() +
                            " must be in CONTROLLED mode, but was: " + session.getMode());
        }
    }

    /**
     * For any set of vehicles started in Controlled Mode, the orchestrator SHALL
     * invoke clientPool.createConnection for each vehicle to establish WebSocket connections.
     *
     * <p><b>Validates: Requirements 5.5</b></p>
     */
    @Property(tries = 100)
    void startControlledMode_createsWebSocketConnectionForEachVehicle(
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.startControlledMode(vehicles);

        // Verify createConnection was called once per vehicle
        for (Vehicle vehicle : vehicles) {
            verify(clientPool).createConnection(eq(vehicle), eq("client_1"));
        }

        // Total invocations should equal the number of vehicles
        verify(clientPool, times(vehicles.size())).createConnection(any(Vehicle.class), anyString());
    }

    /**
     * For any set of vehicles started in Controlled Mode, the orchestrator's
     * current mode SHALL be CONTROLLED.
     *
     * <p><b>Validates: Requirements 5.5</b></p>
     */
    @Property(tries = 100)
    void startControlledMode_setsOrchestratorModeToControlled(
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.startControlledMode(vehicles);

        assertEquals(ConsumptionMode.CONTROLLED, orchestrator.getCurrentMode(),
                "Orchestrator mode must be CONTROLLED after startControlledMode");
    }

    /**
     * For any set of vehicles started in Controlled Mode, all vehicle IDs in the sessions
     * SHALL correspond exactly to the vehicles that were provided.
     *
     * <p><b>Validates: Requirements 5.5</b></p>
     */
    @Property(tries = 100)
    void startControlledMode_sessionVehiclesMatchInputVehicles(
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.startControlledMode(vehicles);

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        Set<String> sessionVehicleIds = sessions.values().stream()
                .map(s -> s.getVehicle().getId())
                .collect(Collectors.toSet());

        Set<String> inputVehicleIds = vehicles.stream()
                .map(Vehicle::getId)
                .collect(Collectors.toSet());

        assertEquals(inputVehicleIds, sessionVehicleIds,
                "Session vehicle IDs must exactly match input vehicle IDs. " +
                        "Input: " + inputVehicleIds + ", sessions: " + sessionVehicleIds);
    }

    // --- Generators ---

    /**
     * Generates non-empty sets of vehicles with unique IDs (1 to 10 vehicles).
     */
    @Provide
    Arbitrary<Set<Vehicle>> vehicleSets() {
        Arbitrary<Vehicle> vehicleArbitrary = Combinators.combine(
                vehicleIds(),
                vehicleNames(),
                realmIds()
        ).as((id, name, realmId) -> new Vehicle(id, name, realmId, VehicleStatus.ACTIVE));

        return vehicleArbitrary.set().ofMinSize(1).ofMaxSize(10);
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
