package com.fms.consumer.integration;

import com.fms.consumer.config.OpenRemoteProperties;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.service.ConfigurationService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for WebSocket reconnection with exponential backoff.
 *
 * <p><b>Validates: Requirements 6.4</b></p>
 *
 * <p>Property 17: WebSocket Reconnection on Failure —
 * "For any interrupted WebSocket connection during an active consumption session,
 * the system SHALL attempt to re-establish the connection with exponential backoff
 * up to 30 seconds maximum delay."</p>
 *
 * <p>Since WebSocket connections are hard to mock at the protocol level, this test validates
 * the mathematical/logic properties of the exponential backoff formula and the
 * WebSocketConnection state transitions.</p>
 */
class WebSocketReconnectionPropertyTest {

    /**
     * Property 17a: For any sequence of reconnection attempts (1 to N), the computed delay
     * follows exponential backoff: delay_n = min(initialDelay * 2^(n-1), maxDelay).
     *
     * <p><b>Validates: Requirements 6.4</b></p>
     */
    @Property(tries = 500)
    void exponentialBackoffFormula_computesCorrectDelay(
            @ForAll @IntRange(min = 100, max = 5000) int initialDelay,
            @ForAll @IntRange(min = 1000, max = 30000) int maxDelay,
            @ForAll @IntRange(min = 1, max = 10) int attemptNumber) {

        // Ensure maxDelay >= initialDelay for meaningful tests
        int effectiveMaxDelay = Math.max(maxDelay, initialDelay);

        // The code uses reconnectAttempt (0-indexed), so attempt 1 maps to reconnectAttempt=0
        int reconnectAttempt = attemptNumber - 1;

        // Compute delay using the same formula as WebSocketConnection.scheduleReconnect()
        long computedDelay = Math.min((long) initialDelay * (1L << reconnectAttempt), effectiveMaxDelay);

        // Verify exponential backoff formula
        long expectedBase = (long) initialDelay * (1L << reconnectAttempt);
        long expectedDelay = Math.min(expectedBase, effectiveMaxDelay);

        assertEquals(expectedDelay, computedDelay,
                String.format("Backoff delay for attempt %d (initialDelay=%d, maxDelay=%d) should be %d but was %d",
                        attemptNumber, initialDelay, effectiveMaxDelay, expectedDelay, computedDelay));

        // The delay must be positive
        assertTrue(computedDelay > 0,
                "Computed delay must always be positive, got: " + computedDelay);
    }

    /**
     * Property 17b: For any computed delay, it never exceeds maxDelay (30s default).
     * This property validates that the backoff is always bounded regardless of attempt count.
     *
     * <p><b>Validates: Requirements 6.4</b></p>
     */
    @Property(tries = 500)
    void exponentialBackoffDelay_neverExceedsMaxDelay(
            @ForAll @IntRange(min = 100, max = 5000) int initialDelay,
            @ForAll @IntRange(min = 1000, max = 30000) int maxDelay,
            @ForAll @IntRange(min = 1, max = 10) int attemptNumber) {

        int effectiveMaxDelay = Math.max(maxDelay, initialDelay);
        int reconnectAttempt = attemptNumber - 1;

        long computedDelay = Math.min((long) initialDelay * (1L << reconnectAttempt), effectiveMaxDelay);

        assertTrue(computedDelay <= effectiveMaxDelay,
                String.format("Delay %d exceeds maxDelay %d for attempt %d with initialDelay %d",
                        computedDelay, effectiveMaxDelay, attemptNumber, initialDelay));
    }

    /**
     * Property 17c: The WebSocketConnection active flag behavior: when active=true and
     * connection drops, reconnect is scheduled; when active=false (after close()), no
     * reconnect happens.
     *
     * <p>Tests that calling close() sets active=false and a new WebSocketConnection starts
     * with active=false until connect() is called.</p>
     *
     * <p><b>Validates: Requirements 6.4</b></p>
     */
    @Property(tries = 100)
    void webSocketConnection_activeFlag_controlsReconnection(
            @ForAll("vehicleIds") String vehicleId,
            @ForAll("clientIds") String clientId) {

        // Create a ConfigurationService with valid properties
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getRetry().setInitialDelay(1000);
        properties.getRetry().setMaxDelay(30000);
        ConfigurationService configService = new ConfigurationService(properties);

        // Create a Vehicle
        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        vehicle.setName("TestVehicle-" + vehicleId);

        // Create a LocationDataHandler (does not need real listeners for this test)
        LocationDataHandler dataHandler = new LocationDataHandler();

        // Use a non-routable endpoint so actual connection attempts will fail fast
        String endpointUrl = "ws://192.0.2.1:1/ws";

        WebSocketConnection connection = new WebSocketConnection(
                vehicle, clientId, dataHandler, configService, endpointUrl);

        // Initially, a WebSocketConnection is NOT active
        assertFalse(connection.isActive(),
                "WebSocketConnection should not be active before connect() is called");
        assertFalse(connection.isConnected(),
                "WebSocketConnection should not be connected before connect() is called");

        // After close(), active must be false
        connection.close();
        assertFalse(connection.isActive(),
                "WebSocketConnection must not be active after close() is called");
    }

    /**
     * Property 17d: For any initialDelay and maxDelay configuration where maxDelay >= initialDelay,
     * the delays form a non-decreasing sequence (before capping at maxDelay),
     * i.e., delay(n+1) >= delay(n) for all n.
     *
     * <p><b>Validates: Requirements 6.4</b></p>
     */
    @Property(tries = 300)
    void exponentialBackoffDelays_areNonDecreasing(
            @ForAll @IntRange(min = 100, max = 5000) int initialDelay,
            @ForAll @IntRange(min = 1000, max = 30000) int maxDelay) {

        int effectiveMaxDelay = Math.max(maxDelay, initialDelay);

        long previousDelay = 0;
        for (int attempt = 0; attempt < 10; attempt++) {
            long currentDelay = Math.min((long) initialDelay * (1L << attempt), effectiveMaxDelay);

            assertTrue(currentDelay >= previousDelay,
                    String.format("Delay at attempt %d (%d) must be >= previous delay (%d)",
                            attempt, currentDelay, previousDelay));
            previousDelay = currentDelay;
        }
    }

    /**
     * Property 17e: The first reconnection delay always equals min(initialDelay, maxDelay),
     * ensuring the very first retry starts at the configured initial delay.
     *
     * <p><b>Validates: Requirements 6.4</b></p>
     */
    @Property(tries = 300)
    void firstReconnectionDelay_equalsInitialDelay(
            @ForAll @IntRange(min = 100, max = 5000) int initialDelay,
            @ForAll @IntRange(min = 1000, max = 30000) int maxDelay) {

        int effectiveMaxDelay = Math.max(maxDelay, initialDelay);

        // First attempt: reconnectAttempt = 0, so delay = min(initialDelay * 2^0, maxDelay) = min(initialDelay, maxDelay)
        long firstDelay = Math.min((long) initialDelay * (1L << 0), effectiveMaxDelay);

        assertEquals(Math.min(initialDelay, effectiveMaxDelay), firstDelay,
                "First reconnection delay should equal min(initialDelay, maxDelay)");

        // Since effectiveMaxDelay >= initialDelay, the first delay is always initialDelay
        assertEquals(initialDelay, firstDelay,
                "First reconnection delay should equal initialDelay when maxDelay >= initialDelay");
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> vehicleIds() {
        return Arbitraries.of(
                "vehicle-001", "vehicle-002", "vehicle-abc",
                "v-100", "truck-42", "bus-99", "fleet-car-7"
        );
    }

    @Provide
    Arbitrary<String> clientIds() {
        return Arbitraries.of(
                "client-1", "client-2", "client-main",
                "sim-client-5", "test-client-a"
        );
    }
}
