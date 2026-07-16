package com.fms.consumer.service;

import com.fms.consumer.integration.WebSocketClientPool;
import com.fms.consumer.model.ConsumptionMode;
import com.fms.consumer.model.ConsumptionSession;
import com.fms.consumer.model.Realm;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.model.VehicleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ConsumptionOrchestrator}.
 * Tests mode transitions (start/stop Random Mode, Controlled Mode),
 * multi-client configuration, and session management.
 *
 * Requirements: 4.1, 4.5, 4.6, 5.4, 5.6, 7.1, 7.2, 7.3, 7.4
 */
@ExtendWith(MockitoExtension.class)
class ConsumptionOrchestratorTest {

    @Mock
    private WebSocketClientPool clientPool;

    @Mock
    private LocationPollingService locationPollingService;

    @Mock
    private DiscoveryService discoveryService;

    @Mock
    private ConfigurationService configService;

    private MetricsCollector metricsCollector;
    private ConsumptionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        metricsCollector = new MetricsCollector();
        orchestrator = new ConsumptionOrchestrator(clientPool, locationPollingService, metricsCollector, discoveryService, configService);
    }

    // --- 1. startRandomMode() creates sessions from available vehicles, sets mode to RANDOM ---

    @Test
    void startRandomMode_createsSessionsFromAvailableVehicles() {
        List<Vehicle> vehicles = List.of(
                new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE),
                new Vehicle("v2", "Truck 2", "realm-A", VehicleStatus.ACTIVE)
        );
        Realm realm = new Realm("realm-A", "Realm A", new ArrayList<>(vehicles));
        when(discoveryService.getCachedRealms()).thenReturn(List.of(realm));

        orchestrator.startRandomMode();

        assertEquals(ConsumptionMode.RANDOM, orchestrator.getCurrentMode());
        assertFalse(orchestrator.getActiveSessions().isEmpty());
    }

    @Test
    void startRandomMode_setsCurrentModeToRandom() {
        List<Vehicle> vehicles = List.of(
                new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE)
        );
        Realm realm = new Realm("realm-A", "Realm A", new ArrayList<>(vehicles));
        when(discoveryService.getCachedRealms()).thenReturn(List.of(realm));

        orchestrator.startRandomMode();

        assertEquals(ConsumptionMode.RANDOM, orchestrator.getCurrentMode());
        assertTrue(orchestrator.isRandomModeActive());
    }

    @Test
    void startRandomMode_noVehiclesAvailable_doesNotCreateSessions() {
        when(discoveryService.getCachedRealms()).thenReturn(Collections.emptyList());

        orchestrator.startRandomMode();

        assertTrue(orchestrator.getActiveSessions().isEmpty());
        // Mode remains IDLE when no vehicles are available
        assertEquals(ConsumptionMode.IDLE, orchestrator.getCurrentMode());
    }

    @Test
    void startRandomMode_callsLocationPollingServiceSubscribe() {
        List<Vehicle> vehicles = List.of(
                new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE)
        );
        Realm realm = new Realm("realm-A", "Realm A", new ArrayList<>(vehicles));
        when(discoveryService.getCachedRealms()).thenReturn(List.of(realm));

        orchestrator.startRandomMode();

        verify(locationPollingService, atLeastOnce()).subscribeVehicle(anyString(), anyString());
        verify(locationPollingService, atLeastOnce()).start();
    }

    // --- 2. stopRandomMode() removes RANDOM sessions, transitions to IDLE ---

    @Test
    void stopRandomMode_removesAllRandomSessions() {
        // Start random mode first
        List<Vehicle> vehicles = List.of(
                new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE),
                new Vehicle("v2", "Truck 2", "realm-A", VehicleStatus.ACTIVE)
        );
        Realm realm = new Realm("realm-A", "Realm A", new ArrayList<>(vehicles));
        when(discoveryService.getCachedRealms()).thenReturn(List.of(realm));

        orchestrator.startRandomMode();
        assertFalse(orchestrator.getActiveSessions().isEmpty());

        orchestrator.stopRandomMode();

        assertTrue(orchestrator.getActiveSessions().isEmpty());
    }

    @Test
    void stopRandomMode_transitionsToIdleMode() {
        List<Vehicle> vehicles = List.of(
                new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE)
        );
        Realm realm = new Realm("realm-A", "Realm A", new ArrayList<>(vehicles));
        when(discoveryService.getCachedRealms()).thenReturn(List.of(realm));

        orchestrator.startRandomMode();
        assertEquals(ConsumptionMode.RANDOM, orchestrator.getCurrentMode());

        orchestrator.stopRandomMode();

        assertEquals(ConsumptionMode.IDLE, orchestrator.getCurrentMode());
        assertFalse(orchestrator.isRandomModeActive());
    }

    @Test
    void stopRandomMode_unsubscribesVehiclesFromPolling() {
        List<Vehicle> vehicles = List.of(
                new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE)
        );
        Realm realm = new Realm("realm-A", "Realm A", new ArrayList<>(vehicles));
        when(discoveryService.getCachedRealms()).thenReturn(List.of(realm));

        orchestrator.startRandomMode();
        orchestrator.stopRandomMode();

        verify(locationPollingService, atLeastOnce()).unsubscribeVehicle(anyString());
    }

    // --- 3. startControlledMode(Set<Vehicle>) creates sessions, sets mode to CONTROLLED ---

    @Test
    void startControlledMode_createsSessionsForProvidedVehicles() {
        Set<Vehicle> vehicles = Set.of(
                new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE),
                new Vehicle("v2", "Truck 2", "realm-B", VehicleStatus.ACTIVE)
        );

        orchestrator.startControlledMode(vehicles);

        assertEquals(2, orchestrator.getActiveSessions().size());
        assertEquals(ConsumptionMode.CONTROLLED, orchestrator.getCurrentMode());
    }

    @Test
    void startControlledMode_setsCurrentModeToControlled() {
        Set<Vehicle> vehicles = Set.of(
                new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE)
        );

        orchestrator.startControlledMode(vehicles);

        assertEquals(ConsumptionMode.CONTROLLED, orchestrator.getCurrentMode());
    }

    @Test
    void startControlledMode_subscribesVehiclesForPolling() {
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        Set<Vehicle> vehicles = Set.of(v1);

        orchestrator.startControlledMode(vehicles);

        verify(locationPollingService).subscribeVehicle(eq("v1"), eq("client_1"));
        verify(locationPollingService).start();
    }

    @Test
    void startControlledMode_sessionsHaveControlledMode() {
        Set<Vehicle> vehicles = Set.of(
                new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE)
        );

        orchestrator.startControlledMode(vehicles);

        orchestrator.getActiveSessions().values().forEach(session ->
                assertEquals(ConsumptionMode.CONTROLLED, session.getMode()));
    }

    // --- 4. stopControlledMode(Set<Vehicle>) removes specific vehicle sessions ---

    @Test
    void stopControlledMode_removesSpecificVehicleSessions() {
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        Vehicle v2 = new Vehicle("v2", "Truck 2", "realm-A", VehicleStatus.ACTIVE);
        Set<Vehicle> allVehicles = Set.of(v1, v2);

        orchestrator.startControlledMode(allVehicles);
        assertEquals(2, orchestrator.getActiveSessions().size());

        orchestrator.stopControlledMode(Set.of(v1));

        assertEquals(1, orchestrator.getActiveSessions().size());
        // v2 session should remain
        assertTrue(orchestrator.getActiveSessions().values().stream()
                .anyMatch(s -> s.getVehicle().getId().equals("v2")));
    }

    @Test
    void stopControlledMode_transitionsToIdleWhenNoSessionsRemain() {
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        Set<Vehicle> vehicles = Set.of(v1);

        orchestrator.startControlledMode(vehicles);
        orchestrator.stopControlledMode(vehicles);

        assertEquals(ConsumptionMode.IDLE, orchestrator.getCurrentMode());
        assertTrue(orchestrator.getActiveSessions().isEmpty());
    }

    @Test
    void stopControlledMode_unsubscribesVehiclesFromPolling() {
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        Set<Vehicle> vehicles = Set.of(v1);

        orchestrator.startControlledMode(vehicles);
        orchestrator.stopControlledMode(vehicles);

        verify(locationPollingService).unsubscribeVehicle(eq("v1"));
    }

    // --- 5. configureMultiClient(int) validates bounds (1-100), rejects invalid values ---

    @Test
    void configureMultiClient_validValue_setsClientCount() {
        when(configService.getSimulationMaxClients()).thenReturn(100);

        orchestrator.configureMultiClient(5);

        assertEquals(5, orchestrator.getClientCount());
    }

    @Test
    void configureMultiClient_minimumValue_accepted() {
        when(configService.getSimulationMaxClients()).thenReturn(100);

        orchestrator.configureMultiClient(1);

        assertEquals(1, orchestrator.getClientCount());
    }

    @Test
    void configureMultiClient_maximumValue_accepted() {
        when(configService.getSimulationMaxClients()).thenReturn(100);

        orchestrator.configureMultiClient(100);

        assertEquals(100, orchestrator.getClientCount());
    }

    @Test
    void configureMultiClient_zeroValue_throwsException() {
        when(configService.getSimulationMaxClients()).thenReturn(100);

        assertThrows(IllegalArgumentException.class, () ->
                orchestrator.configureMultiClient(0));
    }

    @Test
    void configureMultiClient_negativeValue_throwsException() {
        when(configService.getSimulationMaxClients()).thenReturn(100);

        assertThrows(IllegalArgumentException.class, () ->
                orchestrator.configureMultiClient(-1));
    }

    @Test
    void configureMultiClient_exceedsMax_throwsException() {
        when(configService.getSimulationMaxClients()).thenReturn(100);

        assertThrows(IllegalArgumentException.class, () ->
                orchestrator.configureMultiClient(101));
    }

    // --- 6. Multiple client configuration creates sessions = vehicles × clients ---

    @Test
    void multiClient_createsSessionsPerVehicleTimesClients() {
        when(configService.getSimulationMaxClients()).thenReturn(100);
        orchestrator.configureMultiClient(3);

        Set<Vehicle> vehicles = Set.of(
                new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE),
                new Vehicle("v2", "Truck 2", "realm-A", VehicleStatus.ACTIVE)
        );

        orchestrator.startControlledMode(vehicles);

        // 2 vehicles × 3 clients = 6 sessions
        assertEquals(6, orchestrator.getActiveSessions().size());
    }

    @Test
    void multiClient_subscribesIndependentlyPerClient() {
        when(configService.getSimulationMaxClients()).thenReturn(100);
        orchestrator.configureMultiClient(2);

        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        Set<Vehicle> vehicles = Set.of(v1);

        orchestrator.startControlledMode(vehicles);

        verify(locationPollingService).subscribeVehicle(eq("v1"), eq("client_1"));
        verify(locationPollingService).subscribeVehicle(eq("v1"), eq("client_2"));
    }

    // --- 7. Starting Controlled Mode twice doesn't duplicate sessions for same vehicle ---

    @Test
    void startControlledMode_twice_doesNotDuplicateSessions() {
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        Set<Vehicle> vehicles = Set.of(v1);

        orchestrator.startControlledMode(vehicles);
        int sessionCountAfterFirst = orchestrator.getActiveSessions().size();

        orchestrator.startControlledMode(vehicles);
        int sessionCountAfterSecond = orchestrator.getActiveSessions().size();

        assertEquals(sessionCountAfterFirst, sessionCountAfterSecond);
    }

    @Test
    void startControlledMode_addingNewVehicle_createsOnlyNewSession() {
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        Vehicle v2 = new Vehicle("v2", "Truck 2", "realm-A", VehicleStatus.ACTIVE);

        orchestrator.startControlledMode(Set.of(v1));
        assertEquals(1, orchestrator.getActiveSessions().size());

        orchestrator.startControlledMode(Set.of(v1, v2));
        assertEquals(2, orchestrator.getActiveSessions().size());
    }

    // --- 8. Empty vehicle set doesn't create sessions ---

    @Test
    void startControlledMode_emptySet_doesNotCreateSessions() {
        orchestrator.startControlledMode(Collections.emptySet());

        assertTrue(orchestrator.getActiveSessions().isEmpty());
        assertEquals(ConsumptionMode.IDLE, orchestrator.getCurrentMode());
    }

    @Test
    void startControlledMode_nullSet_doesNotCreateSessions() {
        orchestrator.startControlledMode(null);

        assertTrue(orchestrator.getActiveSessions().isEmpty());
        assertEquals(ConsumptionMode.IDLE, orchestrator.getCurrentMode());
    }

    @Test
    void stopControlledMode_emptySet_doesNothing() {
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        orchestrator.startControlledMode(Set.of(v1));

        orchestrator.stopControlledMode(Collections.emptySet());

        assertEquals(1, orchestrator.getActiveSessions().size());
    }

    @Test
    void stopControlledMode_nullSet_doesNothing() {
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        orchestrator.startControlledMode(Set.of(v1));

        orchestrator.stopControlledMode(null);

        assertEquals(1, orchestrator.getActiveSessions().size());
    }

    // --- 9. getActiveSessions() returns unmodifiable map ---

    @Test
    void getActiveSessions_returnsUnmodifiableMap() {
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        orchestrator.startControlledMode(Set.of(v1));

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        assertThrows(UnsupportedOperationException.class, () ->
                sessions.put("test_key", new ConsumptionSession("test", v1, "c1", ConsumptionMode.RANDOM)));
    }

    @Test
    void getActiveSessions_returnsEmptyUnmodifiableMap_initially() {
        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        assertTrue(sessions.isEmpty());
        assertThrows(UnsupportedOperationException.class, () ->
                sessions.put("key", new ConsumptionSession(
                        "id", new Vehicle("v", "n", "r", VehicleStatus.ACTIVE), "c", ConsumptionMode.RANDOM)));
    }

    // --- 10. Mode transitions: IDLE → RANDOM → IDLE, IDLE → CONTROLLED → IDLE ---

    @Test
    void modeTransition_idleToRandomToIdle() {
        List<Vehicle> vehicles = List.of(
                new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE)
        );
        Realm realm = new Realm("realm-A", "Realm A", new ArrayList<>(vehicles));
        when(discoveryService.getCachedRealms()).thenReturn(List.of(realm));

        assertEquals(ConsumptionMode.IDLE, orchestrator.getCurrentMode());

        orchestrator.startRandomMode();
        assertEquals(ConsumptionMode.RANDOM, orchestrator.getCurrentMode());

        orchestrator.stopRandomMode();
        assertEquals(ConsumptionMode.IDLE, orchestrator.getCurrentMode());
    }

    @Test
    void modeTransition_idleToControlledToIdle() {
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        Set<Vehicle> vehicles = Set.of(v1);

        assertEquals(ConsumptionMode.IDLE, orchestrator.getCurrentMode());

        orchestrator.startControlledMode(vehicles);
        assertEquals(ConsumptionMode.CONTROLLED, orchestrator.getCurrentMode());

        orchestrator.stopControlledMode(vehicles);
        assertEquals(ConsumptionMode.IDLE, orchestrator.getCurrentMode());
    }

    @Test
    void initialMode_isIdle() {
        assertEquals(ConsumptionMode.IDLE, orchestrator.getCurrentMode());
    }

    @Test
    void getActiveVehicleCount_returnsCorrectCount() {
        when(configService.getSimulationMaxClients()).thenReturn(100);
        orchestrator.configureMultiClient(2);

        Set<Vehicle> vehicles = Set.of(
                new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE),
                new Vehicle("v2", "Truck 2", "realm-B", VehicleStatus.ACTIVE)
        );

        orchestrator.startControlledMode(vehicles);

        // 2 vehicles, despite 2 clients each
        assertEquals(2, orchestrator.getActiveVehicleCount());
    }

    @Test
    void getClientCount_returnsDefaultValueOf1() {
        assertEquals(1, orchestrator.getClientCount());
    }
}
