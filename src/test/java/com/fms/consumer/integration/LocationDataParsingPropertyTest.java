package com.fms.consumer.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fms.consumer.model.LocationData;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for location data parsing in {@link LocationDataHandler}.
 *
 * <p><b>Validates: Requirements 6.2</b></p>
 *
 * <p>Property 16: Location Data Parsing —
 * "For any received Location_Data message containing geographic coordinates and metadata,
 * the system SHALL successfully parse the coordinates and metadata into structured data."</p>
 */
class LocationDataParsingPropertyTest {

    private LocationDataHandler handler;
    private ObjectMapper objectMapper;

    @BeforeProperty
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new LocationDataHandler(objectMapper);
    }

    /**
     * For any valid JSON message with coordinates and metadata, parse() returns non-null LocationData.
     *
     * <p><b>Validates: Requirements 6.2</b></p>
     */
    @Property(tries = 200)
    void validLocationMessage_parsesToNonNull(
            @ForAll("validVehicleIds") String vehicleId,
            @ForAll("validLatitudes") double latitude,
            @ForAll("validLongitudes") double longitude,
            @ForAll("validTimestamps") long timestamp,
            @ForAll("metadataMaps") Map<String, Object> metadata) {

        String json = buildLocationJson(vehicleId, latitude, longitude, timestamp, metadata);

        LocationData result = handler.parse(json);

        assertNotNull(result, "parse() must return non-null for valid input: " + json);
    }

    /**
     * The parsed vehicleId matches the input exactly.
     *
     * <p><b>Validates: Requirements 6.2</b></p>
     */
    @Property(tries = 200)
    void parsedVehicleId_matchesInput(
            @ForAll("validVehicleIds") String vehicleId,
            @ForAll("validLatitudes") double latitude,
            @ForAll("validLongitudes") double longitude,
            @ForAll("validTimestamps") long timestamp,
            @ForAll("metadataMaps") Map<String, Object> metadata) {

        String json = buildLocationJson(vehicleId, latitude, longitude, timestamp, metadata);

        LocationData result = handler.parse(json);

        assertNotNull(result);
        assertEquals(vehicleId, result.getVehicleId(),
                "Parsed vehicleId must match input exactly");
    }

    /**
     * The parsed latitude matches the input exactly.
     *
     * <p><b>Validates: Requirements 6.2</b></p>
     */
    @Property(tries = 200)
    void parsedLatitude_matchesInput(
            @ForAll("validVehicleIds") String vehicleId,
            @ForAll("validLatitudes") double latitude,
            @ForAll("validLongitudes") double longitude,
            @ForAll("validTimestamps") long timestamp,
            @ForAll("metadataMaps") Map<String, Object> metadata) {

        String json = buildLocationJson(vehicleId, latitude, longitude, timestamp, metadata);

        LocationData result = handler.parse(json);

        assertNotNull(result);
        assertEquals(latitude, result.getLatitude(), 0.0000001,
                "Parsed latitude must match input exactly");
    }

    /**
     * The parsed longitude matches the input exactly.
     *
     * <p><b>Validates: Requirements 6.2</b></p>
     */
    @Property(tries = 200)
    void parsedLongitude_matchesInput(
            @ForAll("validVehicleIds") String vehicleId,
            @ForAll("validLatitudes") double latitude,
            @ForAll("validLongitudes") double longitude,
            @ForAll("validTimestamps") long timestamp,
            @ForAll("metadataMaps") Map<String, Object> metadata) {

        String json = buildLocationJson(vehicleId, latitude, longitude, timestamp, metadata);

        LocationData result = handler.parse(json);

        assertNotNull(result);
        assertEquals(longitude, result.getLongitude(), 0.0000001,
                "Parsed longitude must match input exactly");
    }

    /**
     * The parsed metadata contains all input entries.
     *
     * <p><b>Validates: Requirements 6.2</b></p>
     */
    @Property(tries = 200)
    void parsedMetadata_containsAllInputEntries(
            @ForAll("validVehicleIds") String vehicleId,
            @ForAll("validLatitudes") double latitude,
            @ForAll("validLongitudes") double longitude,
            @ForAll("validTimestamps") long timestamp,
            @ForAll("metadataMaps") Map<String, Object> metadata) {

        String json = buildLocationJson(vehicleId, latitude, longitude, timestamp, metadata);

        LocationData result = handler.parse(json);

        assertNotNull(result);
        assertNotNull(result.getMetadata());

        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            assertTrue(result.getMetadata().containsKey(entry.getKey()),
                    "Parsed metadata must contain key: " + entry.getKey());

            Object expected = entry.getValue();
            Object actual = result.getMetadata().get(entry.getKey());

            if (expected instanceof Integer) {
                // Jackson parses integers as Long for integral numbers
                assertEquals(((Integer) expected).longValue(), actual,
                        "Metadata value mismatch for key: " + entry.getKey());
            } else if (expected instanceof Double) {
                assertEquals((Double) expected, (Double) actual, 0.0000001,
                        "Metadata double value mismatch for key: " + entry.getKey());
            } else {
                assertEquals(expected, actual,
                        "Metadata value mismatch for key: " + entry.getKey());
            }
        }
    }

    // --- Generators ---

    @Provide
    Arbitrary<String> validVehicleIds() {
        return Arbitraries.of(
                "v1", "vehicle-001", "TRUCK_42", "bus-alpha-7",
                "fleet-car-99", "VH-12345", "abc", "X",
                "unit_test_vehicle", "realm1-v100"
        );
    }

    @Provide
    Arbitrary<Double> validLatitudes() {
        return Arbitraries.doubles().between(-90.0, 90.0);
    }

    @Provide
    Arbitrary<Double> validLongitudes() {
        return Arbitraries.doubles().between(-180.0, 180.0);
    }

    @Provide
    Arbitrary<Long> validTimestamps() {
        // Positive epoch millis: from 1970-01-01 to ~2100
        return Arbitraries.longs().between(1L, 4102444800000L);
    }

    @Provide
    Arbitrary<Map<String, Object>> metadataMaps() {
        Arbitrary<String> keys = Arbitraries.of(
                "speed", "heading", "altitude", "driver", "status",
                "engineOn", "fuelLevel", "temperature", "route", "zone"
        );

        Arbitrary<Object> values = Arbitraries.oneOf(
                Arbitraries.strings().ofMinLength(1).ofMaxLength(20)
                        .alpha().map(s -> (Object) s),
                Arbitraries.integers().between(0, 1000).map(i -> (Object) i),
                Arbitraries.doubles().between(0.0, 200.0).map(d -> (Object) d),
                Arbitraries.of(true, false).map(b -> (Object) b)
        );

        return Arbitraries.maps(keys, values)
                .ofMinSize(0)
                .ofMaxSize(5);
    }

    // --- Helper ---

    private String buildLocationJson(String vehicleId, double latitude, double longitude,
                                     long timestamp, Map<String, Object> metadata) {
        try {
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("vehicleId", vehicleId);
            jsonMap.put("latitude", latitude);
            jsonMap.put("longitude", longitude);
            jsonMap.put("timestamp", timestamp);
            if (metadata != null && !metadata.isEmpty()) {
                jsonMap.put("metadata", metadata);
            }
            return objectMapper.writeValueAsString(jsonMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build JSON", e);
        }
    }
}
