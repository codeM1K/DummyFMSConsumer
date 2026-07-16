package com.fms.consumer.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocationDataTest {

    @Test
    void defaultConstructor_initializesEmptyMetadata() {
        LocationData data = new LocationData();
        assertNull(data.getVehicleId());
        assertEquals(0.0, data.getLatitude());
        assertEquals(0.0, data.getLongitude());
        assertNull(data.getTimestamp());
        assertNotNull(data.getMetadata());
        assertTrue(data.getMetadata().isEmpty());
    }

    @Test
    void parameterizedConstructor_setsAllFields() {
        Instant now = Instant.now();
        Map<String, Object> metadata = Map.of("speed", 50.0, "heading", 90);
        LocationData data = new LocationData("v1", 37.9838, 23.7275, now, metadata);

        assertEquals("v1", data.getVehicleId());
        assertEquals(37.9838, data.getLatitude(), 0.0001);
        assertEquals(23.7275, data.getLongitude(), 0.0001);
        assertEquals(now, data.getTimestamp());
        assertEquals(2, data.getMetadata().size());
        assertEquals(50.0, data.getMetadata().get("speed"));
    }

    @Test
    void parameterizedConstructor_withNullMetadata_initializesEmptyMap() {
        Instant now = Instant.now();
        LocationData data = new LocationData("v1", 0.0, 0.0, now, null);
        assertNotNull(data.getMetadata());
        assertTrue(data.getMetadata().isEmpty());
    }

    @Test
    void settersAndGetters_workCorrectly() {
        LocationData data = new LocationData();
        Instant now = Instant.now();

        data.setVehicleId("v2");
        data.setLatitude(45.0);
        data.setLongitude(90.0);
        data.setTimestamp(now);
        data.setMetadata(Map.of("key", "value"));

        assertEquals("v2", data.getVehicleId());
        assertEquals(45.0, data.getLatitude());
        assertEquals(90.0, data.getLongitude());
        assertEquals(now, data.getTimestamp());
        assertEquals("value", data.getMetadata().get("key"));
    }

    @Test
    void setMetadata_withNull_initializesEmptyMap() {
        LocationData data = new LocationData("v1", 0.0, 0.0, Instant.now(), Map.of("k", "v"));
        data.setMetadata(null);
        assertNotNull(data.getMetadata());
        assertTrue(data.getMetadata().isEmpty());
    }

    // Coordinate validation tests

    @Test
    void constructor_latitudeAt90_isValid() {
        assertDoesNotThrow(() -> new LocationData("v1", 90.0, 0.0, Instant.now(), null));
    }

    @Test
    void constructor_latitudeAtNegative90_isValid() {
        assertDoesNotThrow(() -> new LocationData("v1", -90.0, 0.0, Instant.now(), null));
    }

    @Test
    void constructor_latitudeAbove90_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new LocationData("v1", 90.1, 0.0, Instant.now(), null));
    }

    @Test
    void constructor_latitudeBelowNegative90_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new LocationData("v1", -90.1, 0.0, Instant.now(), null));
    }

    @Test
    void constructor_longitudeAt180_isValid() {
        assertDoesNotThrow(() -> new LocationData("v1", 0.0, 180.0, Instant.now(), null));
    }

    @Test
    void constructor_longitudeAtNegative180_isValid() {
        assertDoesNotThrow(() -> new LocationData("v1", 0.0, -180.0, Instant.now(), null));
    }

    @Test
    void constructor_longitudeAbove180_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new LocationData("v1", 0.0, 180.1, Instant.now(), null));
    }

    @Test
    void constructor_longitudeBelowNegative180_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new LocationData("v1", 0.0, -180.1, Instant.now(), null));
    }

    @Test
    void setLatitude_validRange_succeeds() {
        LocationData data = new LocationData();
        assertDoesNotThrow(() -> data.setLatitude(0.0));
        assertDoesNotThrow(() -> data.setLatitude(90.0));
        assertDoesNotThrow(() -> data.setLatitude(-90.0));
    }

    @Test
    void setLatitude_outOfRange_throwsException() {
        LocationData data = new LocationData();
        assertThrows(IllegalArgumentException.class, () -> data.setLatitude(91.0));
        assertThrows(IllegalArgumentException.class, () -> data.setLatitude(-91.0));
    }

    @Test
    void setLongitude_validRange_succeeds() {
        LocationData data = new LocationData();
        assertDoesNotThrow(() -> data.setLongitude(0.0));
        assertDoesNotThrow(() -> data.setLongitude(180.0));
        assertDoesNotThrow(() -> data.setLongitude(-180.0));
    }

    @Test
    void setLongitude_outOfRange_throwsException() {
        LocationData data = new LocationData();
        assertThrows(IllegalArgumentException.class, () -> data.setLongitude(181.0));
        assertThrows(IllegalArgumentException.class, () -> data.setLongitude(-181.0));
    }

    // Equals and hashCode tests

    @Test
    void equals_sameFields_returnsTrue() {
        Instant now = Instant.now();
        LocationData d1 = new LocationData("v1", 37.0, 23.0, now, null);
        LocationData d2 = new LocationData("v1", 37.0, 23.0, now, null);
        assertEquals(d1, d2);
    }

    @Test
    void equals_differentVehicleId_returnsFalse() {
        Instant now = Instant.now();
        LocationData d1 = new LocationData("v1", 37.0, 23.0, now, null);
        LocationData d2 = new LocationData("v2", 37.0, 23.0, now, null);
        assertNotEquals(d1, d2);
    }

    @Test
    void equals_differentCoordinates_returnsFalse() {
        Instant now = Instant.now();
        LocationData d1 = new LocationData("v1", 37.0, 23.0, now, null);
        LocationData d2 = new LocationData("v1", 38.0, 23.0, now, null);
        assertNotEquals(d1, d2);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        LocationData d1 = new LocationData("v1", 37.0, 23.0, Instant.now(), null);
        assertNotEquals(d1, null);
    }

    @Test
    void equals_sameObject_returnsTrue() {
        LocationData d1 = new LocationData("v1", 37.0, 23.0, Instant.now(), null);
        assertEquals(d1, d1);
    }

    @Test
    void hashCode_sameFields_returnsSameHash() {
        Instant now = Instant.now();
        LocationData d1 = new LocationData("v1", 37.0, 23.0, now, null);
        LocationData d2 = new LocationData("v1", 37.0, 23.0, now, null);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void toString_returnsNonNull() {
        LocationData data = new LocationData("v1", 37.0, 23.0, Instant.now(), Map.of("key", "val"));
        String str = data.toString();
        assertNotNull(str);
        assertTrue(str.contains("v1"));
        assertTrue(str.contains("37.0"));
        assertTrue(str.contains("23.0"));
    }

    @Test
    void nullVehicleId_handledGracefully() {
        LocationData data = new LocationData(null, 0.0, 0.0, Instant.now(), null);
        assertNull(data.getVehicleId());
        assertNotNull(data.toString());
    }

    @Test
    void emptyMetadataMap_handledGracefully() {
        LocationData data = new LocationData("v1", 0.0, 0.0, Instant.now(), new HashMap<>());
        assertNotNull(data.getMetadata());
        assertTrue(data.getMetadata().isEmpty());
    }
}
