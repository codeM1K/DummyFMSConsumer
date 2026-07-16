package com.fms.consumer.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fms.consumer.model.LocationData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LocationDataHandlerTest {

    private LocationDataHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LocationDataHandler(new ObjectMapper());
    }

    @Test
    void parse_validJson_returnsLocationData() {
        String json = """
                {
                    "vehicleId": "v1",
                    "latitude": 37.9838,
                    "longitude": 23.7275,
                    "timestamp": 1704067200000,
                    "metadata": {"speed": 50.0, "heading": 90, "altitude": 100}
                }
                """;

        LocationData result = handler.parse(json);

        assertNotNull(result);
        assertEquals("v1", result.getVehicleId());
        assertEquals(37.9838, result.getLatitude(), 0.0001);
        assertEquals(23.7275, result.getLongitude(), 0.0001);
        assertEquals(Instant.ofEpochMilli(1704067200000L), result.getTimestamp());
        assertEquals(50.0, result.getMetadata().get("speed"));
        assertEquals(90L, result.getMetadata().get("heading"));
        assertEquals(100L, result.getMetadata().get("altitude"));
    }

    @Test
    void parse_nullInput_returnsNull() {
        assertNull(handler.parse(null));
    }

    @Test
    void parse_emptyString_returnsNull() {
        assertNull(handler.parse(""));
    }

    @Test
    void parse_blankString_returnsNull() {
        assertNull(handler.parse("   "));
    }

    @Test
    void parse_malformedJson_returnsNull() {
        assertNull(handler.parse("{not valid json"));
    }

    @Test
    void parse_missingVehicleId_returnsNull() {
        String json = """
                {
                    "latitude": 37.9838,
                    "longitude": 23.7275,
                    "timestamp": 1704067200000
                }
                """;
        assertNull(handler.parse(json));
    }

    @Test
    void parse_nullVehicleId_returnsNull() {
        String json = """
                {
                    "vehicleId": null,
                    "latitude": 37.9838,
                    "longitude": 23.7275,
                    "timestamp": 1704067200000
                }
                """;
        assertNull(handler.parse(json));
    }

    @Test
    void parse_missingLatitude_returnsNull() {
        String json = """
                {
                    "vehicleId": "v1",
                    "longitude": 23.7275,
                    "timestamp": 1704067200000
                }
                """;
        assertNull(handler.parse(json));
    }

    @Test
    void parse_missingLongitude_returnsNull() {
        String json = """
                {
                    "vehicleId": "v1",
                    "latitude": 37.9838,
                    "timestamp": 1704067200000
                }
                """;
        assertNull(handler.parse(json));
    }

    @Test
    void parse_invalidLatitude_tooHigh_returnsNull() {
        String json = """
                {
                    "vehicleId": "v1",
                    "latitude": 91.0,
                    "longitude": 23.7275,
                    "timestamp": 1704067200000
                }
                """;
        assertNull(handler.parse(json));
    }

    @Test
    void parse_invalidLatitude_tooLow_returnsNull() {
        String json = """
                {
                    "vehicleId": "v1",
                    "latitude": -91.0,
                    "longitude": 23.7275,
                    "timestamp": 1704067200000
                }
                """;
        assertNull(handler.parse(json));
    }

    @Test
    void parse_invalidLongitude_tooHigh_returnsNull() {
        String json = """
                {
                    "vehicleId": "v1",
                    "latitude": 37.9838,
                    "longitude": 181.0,
                    "timestamp": 1704067200000
                }
                """;
        assertNull(handler.parse(json));
    }

    @Test
    void parse_invalidLongitude_tooLow_returnsNull() {
        String json = """
                {
                    "vehicleId": "v1",
                    "latitude": 37.9838,
                    "longitude": -181.0,
                    "timestamp": 1704067200000
                }
                """;
        assertNull(handler.parse(json));
    }

    @Test
    void parse_missingTimestamp_usesCurrentTime() {
        String json = """
                {
                    "vehicleId": "v1",
                    "latitude": 37.9838,
                    "longitude": 23.7275
                }
                """;

        Instant before = Instant.now();
        LocationData result = handler.parse(json);
        Instant after = Instant.now();

        assertNotNull(result);
        assertNotNull(result.getTimestamp());
        assertTrue(!result.getTimestamp().isBefore(before));
        assertTrue(!result.getTimestamp().isAfter(after));
    }

    @Test
    void parse_missingMetadata_returnsEmptyMetadataMap() {
        String json = """
                {
                    "vehicleId": "v1",
                    "latitude": 37.9838,
                    "longitude": 23.7275,
                    "timestamp": 1704067200000
                }
                """;

        LocationData result = handler.parse(json);

        assertNotNull(result);
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().isEmpty());
    }

    @Test
    void parse_nonNumericLatitude_returnsNull() {
        String json = """
                {
                    "vehicleId": "v1",
                    "latitude": "not a number",
                    "longitude": 23.7275,
                    "timestamp": 1704067200000
                }
                """;
        assertNull(handler.parse(json));
    }

    @Test
    void parse_boundaryCoordinates_succeeds() {
        String json = """
                {
                    "vehicleId": "v1",
                    "latitude": 90.0,
                    "longitude": 180.0,
                    "timestamp": 1704067200000
                }
                """;

        LocationData result = handler.parse(json);
        assertNotNull(result);
        assertEquals(90.0, result.getLatitude(), 0.0001);
        assertEquals(180.0, result.getLongitude(), 0.0001);
    }

    @Test
    void parse_negativeBoundaryCoordinates_succeeds() {
        String json = """
                {
                    "vehicleId": "v1",
                    "latitude": -90.0,
                    "longitude": -180.0,
                    "timestamp": 1704067200000
                }
                """;

        LocationData result = handler.parse(json);
        assertNotNull(result);
        assertEquals(-90.0, result.getLatitude(), 0.0001);
        assertEquals(-180.0, result.getLongitude(), 0.0001);
    }

    @Test
    void notifyListeners_callsAllListeners() {
        List<LocationData> received = new ArrayList<>();
        handler.addListener(received::add);
        handler.addListener(received::add);

        LocationData data = new LocationData("v1", 37.0, 23.0, Instant.now(), null);
        handler.notifyListeners(data);

        assertEquals(2, received.size());
    }

    @Test
    void notifyListeners_nullData_doesNotCallListeners() {
        AtomicInteger callCount = new AtomicInteger(0);
        handler.addListener(d -> callCount.incrementAndGet());

        handler.notifyListeners(null);

        assertEquals(0, callCount.get());
    }

    @Test
    void notifyListeners_listenerThrowsException_otherListenersStillNotified() {
        List<LocationData> received = new ArrayList<>();
        handler.addListener(d -> { throw new RuntimeException("test error"); });
        handler.addListener(received::add);

        LocationData data = new LocationData("v1", 37.0, 23.0, Instant.now(), null);
        handler.notifyListeners(data);

        assertEquals(1, received.size());
    }

    @Test
    void addListener_nullListener_doesNotAdd() {
        handler.addListener(null);
        assertEquals(0, handler.getListenerCount());
    }

    @Test
    void removeListener_removesSuccessfully() {
        LocationDataListener listener = d -> {};
        handler.addListener(listener);
        assertEquals(1, handler.getListenerCount());

        handler.removeListener(listener);
        assertEquals(0, handler.getListenerCount());
    }

    @Test
    void removeListener_nullListener_doesNotThrow() {
        assertDoesNotThrow(() -> handler.removeListener(null));
    }

    @Test
    void parse_metadataWithStringValues_parsesCorrectly() {
        String json = """
                {
                    "vehicleId": "v1",
                    "latitude": 37.9838,
                    "longitude": 23.7275,
                    "timestamp": 1704067200000,
                    "metadata": {"driver": "John", "status": "active"}
                }
                """;

        LocationData result = handler.parse(json);

        assertNotNull(result);
        assertEquals("John", result.getMetadata().get("driver"));
        assertEquals("active", result.getMetadata().get("status"));
    }

    @Test
    void parse_metadataWithBooleanValues_parsesCorrectly() {
        String json = """
                {
                    "vehicleId": "v1",
                    "latitude": 37.9838,
                    "longitude": 23.7275,
                    "timestamp": 1704067200000,
                    "metadata": {"engineOn": true, "parked": false}
                }
                """;

        LocationData result = handler.parse(json);

        assertNotNull(result);
        assertEquals(true, result.getMetadata().get("engineOn"));
        assertEquals(false, result.getMetadata().get("parked"));
    }
}
