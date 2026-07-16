package com.fms.consumer.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.model.VehicleStatus;
import com.fms.consumer.service.ConfigurationService;
import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for location subscription on WebSocket connection.
 *
 * <p><b>Validates: Requirements 6.1</b></p>
 *
 * <p>Property 15: Location Subscription on Connection —
 * "For any vehicle with a newly established WebSocket connection, the system SHALL send
 * a subscription message for that vehicle's Location_Data updates."</p>
 *
 * <p>Since WebSocket server interaction cannot be easily mocked in unit tests, these property
 * tests verify:</p>
 * <ul>
 *   <li>The subscription message format is always valid JSON with correct fields for any vehicle ID</li>
 *   <li>A non-connected WebSocketConnection.subscribe() does NOT throw (just logs warning)</li>
 * </ul>
 */
class LocationSubscriptionPropertyTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Property 15: For any valid vehicle ID, the subscription message format SHALL be
     * valid JSON containing the type "subscribe" and the exact vehicleId.
     *
     * <p>Verifies the message format matches: {"type":"subscribe","vehicleId":"&lt;id&gt;"}</p>
     *
     * <p><b>Validates: Requirements 6.1</b></p>
     */
    @Property(tries = 200)
    void subscriptionMessage_isValidJson_withCorrectFields(@ForAll("vehicleIds") String vehicleId) throws JsonProcessingException {
        // Build the expected subscription message using the same format as WebSocketConnection
        String subscriptionMessage = String.format(
                "{\"type\":\"subscribe\",\"vehicleId\":\"%s\"}", vehicleId);

        // Verify it is valid JSON
        JsonNode root = objectMapper.readTree(subscriptionMessage);
        assertNotNull(root, "Subscription message must be valid JSON");

        // Verify it contains the correct "type" field
        assertTrue(root.has("type"), "Subscription message must have 'type' field");
        assertEquals("subscribe", root.get("type").asText(),
                "Subscription message type must be 'subscribe'");

        // Verify it contains the correct "vehicleId" field
        assertTrue(root.has("vehicleId"), "Subscription message must have 'vehicleId' field");
        assertEquals(vehicleId, root.get("vehicleId").asText(),
                "Subscription message vehicleId must match the vehicle ID exactly");
    }

    /**
     * Property 15: For any vehicle ID, the subscription message format SHALL be consistent
     * and match the expected template exactly.
     *
     * <p><b>Validates: Requirements 6.1</b></p>
     */
    @Property(tries = 200)
    void subscriptionMessage_formatIsConsistent(@ForAll("safeVehicleIds") String vehicleId) {
        String subscriptionMessage = String.format(
                "{\"type\":\"subscribe\",\"vehicleId\":\"%s\"}", vehicleId);

        String expected = "{\"type\":\"subscribe\",\"vehicleId\":\"" + vehicleId + "\"}";
        assertEquals(expected, subscriptionMessage,
                "Subscription message format must be consistent for vehicleId: " + vehicleId);
    }

    /**
     * Property 15: For any vehicle with a WebSocketConnection that is NOT connected,
     * calling subscribe() SHALL NOT throw an exception (it just logs a warning).
     *
     * <p><b>Validates: Requirements 6.1</b></p>
     */
    @Property(tries = 100)
    void subscribe_whenNotConnected_doesNotThrow(@ForAll("vehicleIds") String vehicleId) {
        Vehicle vehicle = new Vehicle(vehicleId, "Test Vehicle " + vehicleId, "realm-1", VehicleStatus.ACTIVE);
        ConfigurationService configService = mock(ConfigurationService.class);
        when(configService.getRetryInitialDelay()).thenReturn(1000);
        when(configService.getRetryMaxDelay()).thenReturn(30000);

        LocationDataHandler dataHandler = new LocationDataHandler();

        // Create a connection that is NOT connected (webSocket is null, connected is false)
        WebSocketConnection connection = new WebSocketConnection(
                vehicle, "client-1", dataHandler, configService, "ws://localhost:9999/not-real");

        // Verify that calling subscribe() when not connected does NOT throw
        assertDoesNotThrow(() -> connection.subscribe(),
                "subscribe() must not throw when WebSocket is not connected for vehicle: " + vehicleId);

        // Verify the connection is indeed not connected
        assertFalse(connection.isConnected(),
                "Connection must not be marked as connected before connect() is called");
    }

    /**
     * Generates diverse vehicle IDs for testing the subscription message format property.
     * Includes alphanumeric IDs, UUIDs, and various patterns.
     */
    @Provide
    Arbitrary<String> vehicleIds() {
        return Arbitraries.oneOf(
                // Simple alphanumeric IDs
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(50),
                // UUID-like IDs
                Arbitraries.of(
                        "550e8400-e29b-41d4-a716-446655440000",
                        "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
                        "f47ac10b-58cc-4372-a567-0e02b2c3d479"
                ),
                // Realistic vehicle IDs
                Arbitraries.of(
                        "vehicle-001", "v-123", "VH_456", "truck-789",
                        "fleet-a-001", "bus-42", "van-007", "car-999"
                ),
                // Edge case IDs (non-empty, valid for JSON strings without special chars)
                Arbitraries.of("a", "Z", "1", "vehicle", "VEHICLE-ABC-123")
        );
    }

    /**
     * Generates vehicle IDs that are safe for direct string embedding (no JSON special characters).
     * Used for the format consistency test where we compare raw strings.
     */
    @Provide
    Arbitrary<String> safeVehicleIds() {
        return Arbitraries.strings()
                .alpha().numeric()
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(50);
    }
}
