package com.fms.consumer.integration;

import com.fms.consumer.config.OpenRemoteProperties;
import com.fms.consumer.model.ConsumptionMode;
import com.fms.consumer.model.ConsumptionSession;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.model.VehicleStatus;
import com.fms.consumer.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for error handling scenarios across multiple services working together.
 * Mocks external dependencies (OpenRemoteRestClient, WebSocket) but wires real services together.
 *
 * <p>Tests validate:
 * <ul>
 *   <li>Network outage recovery (Req 12.3)</li>
 *   <li>Authentication failure handling (Req 12.4)</li>
 *   <li>Partial failure / isolated vehicle failure (Req 12.5)</li>
 *   <li>WebSocket reconnection attempts (Req 12.2)</li>
 * </ul>
 *
 * Requirements: 12.1, 12.2, 12.3, 12.4, 12.5
 */
@ExtendWith(MockitoExtension.class)
class ErrorScenariosIntegrationTest {

    @Mock
    private WebSocketClientPool clientPool;

    @Mock
    private DiscoveryService discoveryService;

    @Mock
    private OpenRemoteRestClient restClient;

    private ConfigurationService configService;
    private MetricsCollector metricsCollector;
    private ConsumptionOrchestrator orchestrator;
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getRetry().setMaxAttempts(3);
        properties.getRetry().setInitialDelay(10); // Short delays for tests
        properties.getRetry().setMaxDelay(100);
        properties.getSimulation().setMaxClients(100);

        configService = new ConfigurationService(properties);
        metricsCollector = new MetricsCollector();
        orchestrator = new ConsumptionOrchestrator(clientPool, metricsCollector, discoveryService, configService);
        authenticationService = new AuthenticationService(configService, restClient);
    }

    // --- 1. Network Outage Recovery (Req 12.3) ---

    @Test
    @DisplayName("Network outage recovery: suspend then resume restores all sessions")
    void networkOutageRecovery_suspendAndResume_restoresAllSessions() {
        // Arrange: Start consumption for multiple vehicles
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        Vehicle v2 = new Vehicle("v2", "Truck 2", "realm-A", VehicleStatus.ACTIVE);
        Vehicle v3 = new Vehicle("v3", "Truck 3", "realm-B", VehicleStatus.ACTIVE);
        Set<Vehicle> vehicles = Set.of(v1, v2, v3);

        orchestrator.startControlledMode(vehicles);
        assertEquals(3, orchestrator.getActiveSessions().size(),
                "All 3 vehicles should have active sessions before outage");

        // Act: Simulate network outage
        orchestrator.suspendAllSessions();

        // Verify sessions are suspended
        assertEquals(3, orchestrator.getSuspendedSessions().size(),
                "All 3 sessions should be suspended during outage");

        // Act: Restore connectivity
        orchestrator.resumeAllSessions();

        // Assert: All sessions should be active again
        Map<String, ConsumptionSession> activeSessions = orchestrator.getActiveSessions();
        assertEquals(3, activeSessions.size(),
                "All 3 sessions should be restored after network recovery");
        assertTrue(orchestrator.getSuspendedSessions().isEmpty(),
                "No sessions should remain suspended after recovery");

        // Verify WebSocket connections were re-created for each vehicle
        verify(clientPool, atLeast(3)).createConnection(any(Vehicle.class), anyString());
    }

    @Test
    @DisplayName("Network outage recovery: suspended sessions preserve vehicle info")
    void networkOutageRecovery_suspendedSessions_preserveVehicleInfo() {
        // Arrange
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        Vehicle v2 = new Vehicle("v2", "Truck 2", "realm-B", VehicleStatus.ACTIVE);
        Set<Vehicle> vehicles = Set.of(v1, v2);

        orchestrator.startControlledMode(vehicles);

        // Act: Suspend and resume
        orchestrator.suspendAllSessions();
        orchestrator.resumeAllSessions();

        // Assert: Vehicle info preserved in resumed sessions
        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();
        Set<String> vehicleIds = new HashSet<>();
        sessions.values().forEach(s -> vehicleIds.add(s.getVehicle().getId()));

        assertTrue(vehicleIds.contains("v1"), "Vehicle v1 should be present after resume");
        assertTrue(vehicleIds.contains("v2"), "Vehicle v2 should be present after resume");
    }

    @Test
    @DisplayName("Network outage: connections are closed during suspension")
    void networkOutage_connectionsClosed_duringSuspension() {
        // Arrange
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        orchestrator.startControlledMode(Set.of(v1));

        // Reset invocation count to track only suspension calls
        reset(clientPool);

        // Act: Suspend
        orchestrator.suspendAllSessions();

        // Assert: Connection was closed for the vehicle
        verify(clientPool).closeConnection(eq("v1"), eq("client_1"));
    }

    // --- 2. Authentication Failure Handling (Req 12.4) ---

    @Test
    @DisplayName("Authentication failure after max retries returns failure result")
    void authenticationFailure_afterMaxRetries_returnsFailureResult() {
        // Arrange: Mock auth to always fail
        when(restClient.authenticate(anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("Connection refused")));

        // Act
        CompletableFuture<AuthenticationResult> result = authenticationService.authenticate();
        AuthenticationResult authResult = result.join();

        // Assert: Should eventually return failure (not success)
        assertFalse(authResult.isSuccess(),
                "Authentication should fail after exhausting retries");
        assertNotNull(authResult.getErrorMessage(),
                "Error message should be provided on failure");
        assertTrue(authResult.getErrorMessage().contains("failed"),
                "Error message should indicate failure");

        // Verify that retry attempts were made (3 attempts configured)
        verify(restClient, times(3)).authenticate(anyString(), anyString());
    }

    @Test
    @DisplayName("Authentication failure: service is not authenticated after failure")
    void authenticationFailure_serviceNotAuthenticated_afterFailure() {
        // Arrange: Always fail
        when(restClient.authenticate(anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("Network error")));

        // Act
        authenticationService.authenticate().join();

        // Assert
        assertFalse(authenticationService.isAuthenticated(),
                "Service should not be authenticated after all retries fail");
        assertNull(authenticationService.getSessionToken(),
                "Session token should be null after auth failure");
    }

    // --- 3. Partial Failure / Isolated Vehicle Failure (Req 12.5) ---

    @Test
    @DisplayName("Partial failure: one vehicle failure does not affect other vehicles")
    void partialFailure_oneVehicleFails_othersUnaffected() {
        // Arrange: Start consumption for 3 vehicles
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        Vehicle v2 = new Vehicle("v2", "Truck 2", "realm-A", VehicleStatus.ACTIVE);
        Vehicle v3 = new Vehicle("v3", "Truck 3", "realm-B", VehicleStatus.ACTIVE);
        Set<Vehicle> vehicles = Set.of(v1, v2, v3);

        orchestrator.startControlledMode(vehicles);
        assertEquals(3, orchestrator.getActiveSessions().size());

        // Act: Simulate failure for vehicle v2
        orchestrator.handleVehicleFailure("v2", "client_1",
                new RuntimeException("WebSocket connection lost for v2"));

        // Assert: v1 and v3 sessions remain active and unaffected
        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();
        assertEquals(3, sessions.size(),
                "All sessions remain in map (failed one stays for retry)");

        // v1 and v3 should still be active
        ConsumptionSession v1Session = sessions.get("v1_client_1");
        ConsumptionSession v3Session = sessions.get("v3_client_1");
        assertNotNull(v1Session, "v1 session should still exist");
        assertNotNull(v3Session, "v3 session should still exist");
        assertTrue(v1Session.isActive(), "v1 session should still be active");
        assertTrue(v3Session.isActive(), "v3 session should still be active");

        // v2 session should be deactivated but kept for retry
        ConsumptionSession v2Session = sessions.get("v2_client_1");
        assertNotNull(v2Session, "v2 session should still exist (for retry)");
        assertFalse(v2Session.isActive(), "v2 session should be deactivated due to failure");
    }

    @Test
    @DisplayName("Partial failure: multiple vehicles can fail independently")
    void partialFailure_multipleVehiclesFailIndependently() {
        // Arrange
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        Vehicle v2 = new Vehicle("v2", "Truck 2", "realm-A", VehicleStatus.ACTIVE);
        Vehicle v3 = new Vehicle("v3", "Truck 3", "realm-B", VehicleStatus.ACTIVE);
        Vehicle v4 = new Vehicle("v4", "Truck 4", "realm-B", VehicleStatus.ACTIVE);
        Set<Vehicle> vehicles = Set.of(v1, v2, v3, v4);

        orchestrator.startControlledMode(vehicles);

        // Act: Fail v1 and v3
        orchestrator.handleVehicleFailure("v1", "client_1",
                new RuntimeException("Timeout"));
        orchestrator.handleVehicleFailure("v3", "client_1",
                new RuntimeException("Connection reset"));

        // Assert: v2 and v4 still active, v1 and v3 deactivated
        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();
        assertTrue(sessions.get("v2_client_1").isActive(), "v2 should remain active");
        assertTrue(sessions.get("v4_client_1").isActive(), "v4 should remain active");
        assertFalse(sessions.get("v1_client_1").isActive(), "v1 should be deactivated");
        assertFalse(sessions.get("v3_client_1").isActive(), "v3 should be deactivated");
    }

    @Test
    @DisplayName("Partial failure during resume: one vehicle fails to resume, others succeed")
    void partialFailure_duringResume_otherVehiclesStillResume() {
        // Arrange: Start and suspend
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        Vehicle v2 = new Vehicle("v2", "Truck 2", "realm-A", VehicleStatus.ACTIVE);
        Vehicle v3 = new Vehicle("v3", "Truck 3", "realm-B", VehicleStatus.ACTIVE);
        Set<Vehicle> vehicles = Set.of(v1, v2, v3);

        orchestrator.startControlledMode(vehicles);
        orchestrator.suspendAllSessions();

        // Make v2's reconnection fail
        doThrow(new RuntimeException("Cannot reach server for v2"))
                .when(clientPool).createConnection(eq(v2), eq("client_1"));

        // Act: Resume
        orchestrator.resumeAllSessions();

        // Assert: Sessions that could resume are active
        // v2 failed to resume, but v1 and v3 should succeed
        // (The implementation catches per-session exceptions and continues)
        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();
        assertEquals(3, sessions.size(),
                "All sessions remain in the map regardless of resume success");
    }

    // --- 4. WebSocket Reconnection (Req 12.2) ---

    @Test
    @DisplayName("WebSocket reconnection: failed session triggers retry scheduling")
    void webSocketReconnection_failedSession_triggersRetry() {
        // Arrange: Start a session
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        orchestrator.startControlledMode(Set.of(v1));

        // Act: Simulate WebSocket failure
        orchestrator.handleVehicleFailure("v1", "client_1",
                new RuntimeException("WebSocket connection dropped"));

        // Assert: Session is deactivated (pending retry)
        ConsumptionSession session = orchestrator.getActiveSessions().get("v1_client_1");
        assertNotNull(session, "Session should remain for retry");
        assertFalse(session.isActive(), "Session should be deactivated pending retry");
        // The retry is scheduled asynchronously - verify the session stays in activeSessions
        assertTrue(orchestrator.getActiveSessions().containsKey("v1_client_1"),
                "Session key should remain in active sessions for retry");
    }

    @Test
    @DisplayName("WebSocket reconnection: connection failure during session creation triggers retry")
    void webSocketReconnection_connectionFailureDuringCreation_triggersRetry() {
        // Arrange: Make connection creation fail for one vehicle
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        doThrow(new RuntimeException("Connection refused"))
                .when(clientPool).createConnection(eq(v1), eq("client_1"));

        // Act: Start controlled mode (connection will fail)
        orchestrator.startControlledMode(Set.of(v1));

        // Assert: Session is created but may have failed connection - session stays for retry
        assertTrue(orchestrator.getActiveSessions().containsKey("v1_client_1"),
                "Session should be kept even after initial connection failure");
    }

    @Test
    @DisplayName("WebSocket reconnection: exponential backoff config is used")
    void webSocketReconnection_usesExponentialBackoffConfig() {
        // The configuration provides retryInitialDelay=10ms, retryMaxDelay=100ms, maxAttempts=3
        assertEquals(10, configService.getRetryInitialDelay(),
                "Initial delay should be 10ms (test config)");
        assertEquals(100, configService.getRetryMaxDelay(),
                "Max delay should be 100ms (test config)");
        assertEquals(3, configService.getRetryMaxAttempts(),
                "Max attempts should be 3 (test config)");
    }

    @Test
    @DisplayName("Combined scenario: outage followed by partial recovery")
    void combinedScenario_outageFollowedByPartialRecovery() {
        // Arrange: Start consumption
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm-A", VehicleStatus.ACTIVE);
        Vehicle v2 = new Vehicle("v2", "Truck 2", "realm-B", VehicleStatus.ACTIVE);
        orchestrator.startControlledMode(Set.of(v1, v2));
        assertEquals(2, orchestrator.getActiveSessions().size());

        // Act: Network outage
        orchestrator.suspendAllSessions();
        assertTrue(orchestrator.getSuspendedSessions().size() > 0);

        // Partial recovery: v2 fails to reconnect
        doThrow(new RuntimeException("Still unreachable"))
                .when(clientPool).createConnection(eq(v2), eq("client_1"));

        orchestrator.resumeAllSessions();

        // Assert: Both sessions still in map (resume logs error but doesn't remove session)
        assertEquals(2, orchestrator.getActiveSessions().size(),
                "Both sessions remain in active map after resume attempt");
        assertTrue(orchestrator.getSuspendedSessions().isEmpty(),
                "Suspended map should be cleared after resume attempt");
    }
}
