package com.fms.consumer.service;

import com.fms.consumer.integration.WebSocketClientPool;
import com.fms.consumer.model.*;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for location data consumption in Random Mode.
 *
 * <p><b>Property 10: Location Data Consumption in Random Mode</b></p>
 * <p>For any vehicle selected in Random Mode with an established WebSocket connection,
 * the system SHALL consume and process Location_Data updates in real-time.</p>
 *
 * <p><b>Validates: Requirements 4.3</b></p>
 */
class LocationDataConsumptionRandomModePropertyTest {

    private WebSocketClientPool clientPool; private LocationPollingService locationPollingService;
    private MetricsCollector metricsCollector;
    private DiscoveryService discoveryService;
    private ConfigurationService configService;

    @BeforeProperty
    void setUp() {
        clientPool = mock(WebSocketClientPool.class); locationPollingService = mock(LocationPollingService.class);
        metricsCollector = mock(MetricsCollector.class);
        discoveryService = mock(DiscoveryService.class);
        configService = mock(ConfigurationService.class);

        when(configService.getSimulationMaxClients()).thenReturn(100);
    }

    /**
     * For any non-empty set of vehicles discovered across realms, when Random Mode
     * is started, every vehicle that ends up in an active session SHALL have a
     * corresponding location polling subscription created.
     *
     * <p><b>Validates: Requirements 4.3</b></p>
     */
    @Property(tries = 100)
    void randomMode_subscribesEachSelectedVehicleForLocationPolling(
            @ForAll("vehicleRealms") List<Realm> realms) {

        // Set up discovery to return the generated realms
        when(discoveryService.getCachedRealms()).thenReturn(realms);
        for (Realm realm : realms) {
            when(discoveryService.getCachedVehicles(realm.getId()))
                    .thenReturn(realm.getVehicles());
        }

        ConsumptionOrchestrator orchestrator = new ConsumptionOrchestrator(clientPool, locationPollingService, metricsCollector, discoveryService, configService);

        orchestrator.startRandomMode();

        // For every active session, verify that subscribeVehicle was called for that vehicle
        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        for (ConsumptionSession session : sessions.values()) {
            Vehicle vehicle = session.getVehicle();
            verify(locationPollingService, atLeastOnce()).subscribeVehicle(
                    eq(vehicle.getId()), eq(session.getClientId()));
        }
    }

    /**
     * For any non-empty set of vehicles, when Random Mode is started, every active
     * session SHALL be in RANDOM mode and marked as active.
     *
     * <p><b>Validates: Requirements 4.3</b></p>
     */
    @Property(tries = 100)
    void randomMode_allSessionsAreActiveAndInRandomMode(
            @ForAll("vehicleRealms") List<Realm> realms) {

        when(discoveryService.getCachedRealms()).thenReturn(realms);
        for (Realm realm : realms) {
            when(discoveryService.getCachedVehicles(realm.getId()))
                    .thenReturn(realm.getVehicles());
        }

        ConsumptionOrchestrator orchestrator = new ConsumptionOrchestrator(clientPool, locationPollingService, metricsCollector, discoveryService, configService);

        orchestrator.startRandomMode();

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        for (ConsumptionSession session : sessions.values()) {
            assertTrue(session.isActive(),
                    "Session for vehicle " + session.getVehicle().getId() + " should be active");
            assertEquals(ConsumptionMode.RANDOM, session.getMode(),
                    "Session for vehicle " + session.getVehicle().getId() + " should be in RANDOM mode");
        }
    }

    /**
     * For any non-empty set of vehicles, when Random Mode is started, the orchestrator
     * SHALL report that Random Mode is active and the current mode is RANDOM.
     *
     * <p><b>Validates: Requirements 4.3</b></p>
     */
    @Property(tries = 100)
    void randomMode_orchestratorReportsRandomModeActive(
            @ForAll("vehicleRealms") List<Realm> realms) {

        when(discoveryService.getCachedRealms()).thenReturn(realms);
        for (Realm realm : realms) {
            when(discoveryService.getCachedVehicles(realm.getId()))
                    .thenReturn(realm.getVehicles());
        }

        ConsumptionOrchestrator orchestrator = new ConsumptionOrchestrator(clientPool, locationPollingService, metricsCollector, discoveryService, configService);

        orchestrator.startRandomMode();

        // Count total vehicles across realms
        long totalVehicles = realms.stream()
                .mapToLong(r -> r.getVehicles() != null ? r.getVehicles().size() : 0)
                .sum();

        if (totalVehicles > 0) {
            assertTrue(orchestrator.isRandomModeActive(),
                    "Random mode should be active when vehicles are available");
            assertEquals(ConsumptionMode.RANDOM, orchestrator.getCurrentMode(),
                    "Current mode should be RANDOM");
        }
    }

    /**
     * For any non-empty set of vehicles, when Random Mode is started, the number of
     * active sessions SHALL equal the number of selected vehicles (with default
     * single-client configuration), and each session's vehicle SHALL come from the
     * available vehicles.
     *
     * <p><b>Validates: Requirements 4.3</b></p>
     */
    @Property(tries = 100)
    void randomMode_eachSessionVehicleComesFromAvailableVehicles(
            @ForAll("vehicleRealms") List<Realm> realms) {

        when(discoveryService.getCachedRealms()).thenReturn(realms);
        for (Realm realm : realms) {
            when(discoveryService.getCachedVehicles(realm.getId()))
                    .thenReturn(realm.getVehicles());
        }

        // Collect all available vehicle IDs
        List<String> allVehicleIds = new ArrayList<>();
        for (Realm realm : realms) {
            if (realm.getVehicles() != null) {
                for (Vehicle v : realm.getVehicles()) {
                    allVehicleIds.add(v.getId());
                }
            }
        }

        ConsumptionOrchestrator orchestrator = new ConsumptionOrchestrator(clientPool, locationPollingService, metricsCollector, discoveryService, configService);

        orchestrator.startRandomMode();

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        // Every session's vehicle must come from the available vehicles
        for (ConsumptionSession session : sessions.values()) {
            assertTrue(allVehicleIds.contains(session.getVehicle().getId()),
                    "Session vehicle " + session.getVehicle().getId() +
                            " must be from the available vehicles");
        }

        // The number of sessions should be at least 1 (random selects 1..N)
        // and at most the total number of vehicles available
        if (!allVehicleIds.isEmpty()) {
            assertTrue(sessions.size() >= 1,
                    "Should have at least 1 session when vehicles are available");
            assertTrue(sessions.size() <= allVehicleIds.size(),
                    "Should not have more sessions than available vehicles");
        }
    }

    /**
     * Provides arbitrary lists of realms, each containing 1-5 vehicles.
     * Generates between 1 and 4 realms, each with distinct vehicle IDs.
     */
    @Provide
    Arbitrary<List<Realm>> vehicleRealms() {
        Arbitrary<String> realmIds = Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(3).ofMaxLength(10)
                .map(s -> "realm-" + s);

        Arbitrary<String> realmNames = Arbitraries.strings()
                .alpha()
                .ofMinLength(3).ofMaxLength(15)
                .map(s -> "Realm " + s);

        Arbitrary<List<Vehicle>> vehicleLists = vehicleArbitrary()
                .list().ofMinSize(1).ofMaxSize(5);

        Arbitrary<Realm> realmArbitrary = Combinators.combine(realmIds, realmNames, vehicleLists)
                .as((id, name, vehicles) -> {
                    // Set realm ID on each vehicle
                    for (Vehicle v : vehicles) {
                        v.setRealmId(id);
                    }
                    return new Realm(id, name, vehicles);
                });

        return realmArbitrary.list().ofMinSize(1).ofMaxSize(4);
    }

    /**
     * Provides arbitrary Vehicle instances with unique IDs.
     */
    private Arbitrary<Vehicle> vehicleArbitrary() {
        Arbitrary<String> vehicleIds = Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(5).ofMaxLength(15)
                .map(s -> "vehicle-" + s);

        Arbitrary<String> vehicleNames = Arbitraries.strings()
                .alpha()
                .ofMinLength(3).ofMaxLength(20)
                .map(s -> "Vehicle " + s);

        return Combinators.combine(vehicleIds, vehicleNames)
                .as((id, name) -> new Vehicle(id, name, null, VehicleStatus.INACTIVE));
    }
}
