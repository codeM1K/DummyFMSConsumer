package com.fms.consumer.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fms.consumer.config.OpenRemoteProperties;
import com.fms.consumer.model.LocationData;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.model.VehicleStatus;
import com.fms.consumer.service.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WebSocket components: WebSocketConnection, WebSocketClientPool,
 * and LocationDataHandler integration.
 *
 * <p>Requirements: 4.2, 4.4, 5.4, 5.6, 6.1, 6.2, 6.4</p>
 *
 * <p>Since we cannot easily create a real WebSocket server in unit tests,
 * we test pool management logic and connection state management using a
 * non-routable endpoint (192.0.2.1 - TEST-NET, RFC 5737) so connections
 * will not actually establish but pool tracking logic can still be validated.</p>
 */
class WebSocketComponentsTest {

    private ConfigurationService configService;
    private LocationDataHandler dataHandler;

    // Non-routable endpoint - connections will fail but pool management is testable
    private static final String NON_ROUTABLE_ENDPOINT = "ws://192.0.2.1:9999/ws/location";

    @BeforeEach
    void setUp() {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getApi().setEndpoint("http://192.0.2.1:9999");
        properties.getRetry().setInitialDelay(1000);
        properties.getRetry().setMaxDelay(30000);
        properties.getRetry().setMaxAttempts(3);
        properties.getConnection().setEstablishment(2000);
        properties.getConnection().setTimeout(5000);
        configService = new ConfigurationService(properties);
        dataHandler = new LocationDataHandler(new ObjectMapper());
    }

    private Vehicle createVehicle(String id, String name, String realmId) {
        return new Vehicle(id, name, realmId, VehicleStatus.ACTIVE);
    }

    // ========================================================================
    // WebSocketConnection Tests
    // ========================================================================

    @Nested
    @DisplayName("WebSocketConnection Tests")
    class WebSocketConnectionTests {

        @Test
        @DisplayName("1. Constructor initializes fields correctly")
        void constructor_initializesFieldsCorrectly() {
            Vehicle vehicle = createVehicle("v1", "Truck A", "realm-1");

            WebSocketConnection conn = new WebSocketConnection(
                    vehicle, "client-1", dataHandler, configService, NON_ROUTABLE_ENDPOINT);

            assertEquals(vehicle, conn.getVehicle());
            assertEquals("client-1", conn.getClientId());
            assertFalse(conn.isActive());
            assertFalse(conn.isConnected());
        }

        @Test
        @DisplayName("2. getVehicle() and getClientId() return correct values")
        void getters_returnCorrectValues() {
            Vehicle vehicle = createVehicle("vehicle-42", "Van B", "realm-2");

            WebSocketConnection conn = new WebSocketConnection(
                    vehicle, "client-99", dataHandler, configService, NON_ROUTABLE_ENDPOINT);

            assertSame(vehicle, conn.getVehicle());
            assertEquals("vehicle-42", conn.getVehicle().getId());
            assertEquals("Van B", conn.getVehicle().getName());
            assertEquals("client-99", conn.getClientId());
        }

        @Test
        @DisplayName("3. isActive() is false initially, becomes true after connect() is called")
        void connect_setsActiveToTrue() {
            Vehicle vehicle = createVehicle("v1", "Truck A", "realm-1");

            WebSocketConnection conn = new WebSocketConnection(
                    vehicle, "client-1", dataHandler, configService, NON_ROUTABLE_ENDPOINT);

            assertFalse(conn.isActive(), "Should be inactive before connect");

            // connect() sets active=true even if the connection itself fails
            conn.connect();

            assertTrue(conn.isActive(), "Should be active after connect() is called");

            // Clean up
            conn.close();
        }

        @Test
        @DisplayName("4. close() sets active=false and connected=false")
        void close_setsActiveAndConnectedToFalse() {
            Vehicle vehicle = createVehicle("v1", "Truck A", "realm-1");

            WebSocketConnection conn = new WebSocketConnection(
                    vehicle, "client-1", dataHandler, configService, NON_ROUTABLE_ENDPOINT);

            conn.connect();
            assertTrue(conn.isActive());

            conn.close();

            assertFalse(conn.isActive(), "Should be inactive after close");
            assertFalse(conn.isConnected(), "Should be disconnected after close");
        }

        @Test
        @DisplayName("5. subscribe() when not connected does not throw")
        void subscribe_whenNotConnected_doesNotThrow() {
            Vehicle vehicle = createVehicle("v1", "Truck A", "realm-1");

            WebSocketConnection conn = new WebSocketConnection(
                    vehicle, "client-1", dataHandler, configService, NON_ROUTABLE_ENDPOINT);

            // Should not throw - just logs a warning
            assertDoesNotThrow(conn::subscribe);
        }

        @Test
        @DisplayName("Connection establishment attempt returns future within 2 seconds")
        void connect_returnsFutureWithinTimeout() {
            Vehicle vehicle = createVehicle("v1", "Truck A", "realm-1");

            // Use a connection-refused endpoint (localhost port unlikely to be listening)
            WebSocketConnection conn = new WebSocketConnection(
                    vehicle, "client-1", dataHandler, configService, "ws://127.0.0.1:1/ws/location");

            long startTime = System.currentTimeMillis();
            CompletableFuture<Void> future = conn.connect();

            // connect() should return a future immediately (non-blocking)
            assertNotNull(future, "connect() should return a non-null future");
            long elapsed = System.currentTimeMillis() - startTime;

            // The connect() call itself (returning the future) should be near-instant
            assertTrue(elapsed < 2000, "connect() should return within 2 seconds, took " + elapsed + "ms");

            // The future should eventually complete (with connection failure to a refused port)
            assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));

            conn.close();
        }

        @Test
        @DisplayName("close() completes quickly (within 1 second)")
        void close_completesWithinOneSecond() {
            Vehicle vehicle = createVehicle("v1", "Truck A", "realm-1");

            WebSocketConnection conn = new WebSocketConnection(
                    vehicle, "client-1", dataHandler, configService, NON_ROUTABLE_ENDPOINT);

            conn.connect();

            long startTime = System.currentTimeMillis();
            conn.close();
            long elapsed = System.currentTimeMillis() - startTime;

            assertTrue(elapsed < 1000, "close() should complete within 1 second, took " + elapsed + "ms");
        }
    }

    // ========================================================================
    // WebSocketClientPool Tests
    // ========================================================================

    @Nested
    @DisplayName("WebSocketClientPool Tests")
    class WebSocketClientPoolTests {

        private WebSocketClientPool pool;

        @BeforeEach
        void setUpPool() {
            pool = new WebSocketClientPool(dataHandler, configService);
        }

        @Test
        @DisplayName("6. Pool starts empty (getTotalConnectionCount() == 0)")
        void pool_startsEmpty() {
            assertEquals(0, pool.getTotalConnectionCount());
        }

        @Test
        @DisplayName("7. createConnection() adds a connection to the pool")
        void createConnection_addsToPool() throws Exception {
            Vehicle vehicle = createVehicle("v1", "Truck A", "realm-1");

            CompletableFuture<WebSocketConnection> future = pool.createConnection(vehicle, "client-1");
            WebSocketConnection conn = future.get(3, TimeUnit.SECONDS);

            assertNotNull(conn);
            assertEquals(1, pool.getTotalConnectionCount());

            pool.closeAllConnections();
        }

        @Test
        @DisplayName("8. closeConnection(vehicleId, clientId) removes the connection")
        void closeConnection_withBothIds_removesFromPool() throws Exception {
            Vehicle vehicle = createVehicle("v1", "Truck A", "realm-1");

            pool.createConnection(vehicle, "client-1").get(3, TimeUnit.SECONDS);
            assertEquals(1, pool.getTotalConnectionCount());

            pool.closeConnection("v1", "client-1");
            assertEquals(0, pool.getTotalConnectionCount());
        }

        @Test
        @DisplayName("9. closeAllConnections() empties the pool")
        void closeAllConnections_emptiesPool() throws Exception {
            Vehicle v1 = createVehicle("v1", "Truck A", "realm-1");
            Vehicle v2 = createVehicle("v2", "Van B", "realm-1");

            pool.createConnection(v1, "client-1").get(3, TimeUnit.SECONDS);
            pool.createConnection(v2, "client-1").get(3, TimeUnit.SECONDS);
            assertEquals(2, pool.getTotalConnectionCount());

            pool.closeAllConnections();
            assertEquals(0, pool.getTotalConnectionCount());
        }

        @Test
        @DisplayName("10. getConnection() returns null for unknown keys")
        void getConnection_unknownKey_returnsNull() {
            assertNull(pool.getConnection("unknown-vehicle", "unknown-client"));
        }

        @Test
        @DisplayName("11. getActiveConnectionCount() returns 0 when all connections are disconnected")
        void getActiveConnectionCount_allDisconnected_returnsZero() throws Exception {
            Vehicle vehicle = createVehicle("v1", "Truck A", "realm-1");

            // Connection will fail to connect to non-routable endpoint
            pool.createConnection(vehicle, "client-1").get(3, TimeUnit.SECONDS);

            // Since the endpoint is non-routable, no connection will be established
            assertEquals(0, pool.getActiveConnectionCount());

            pool.closeAllConnections();
        }

        @Test
        @DisplayName("12. closeConnection(vehicleId) closes all connections for that vehicle")
        void closeConnection_byVehicleId_closesAllForVehicle() throws Exception {
            Vehicle vehicle = createVehicle("v1", "Truck A", "realm-1");

            pool.createConnection(vehicle, "client-1").get(3, TimeUnit.SECONDS);
            pool.createConnection(vehicle, "client-2").get(3, TimeUnit.SECONDS);
            assertEquals(2, pool.getTotalConnectionCount());

            pool.closeConnection("v1");
            assertEquals(0, pool.getTotalConnectionCount());
        }

        @Test
        @DisplayName("closeConnection(vehicleId) does not affect other vehicles")
        void closeConnection_byVehicleId_doesNotAffectOtherVehicles() throws Exception {
            Vehicle v1 = createVehicle("v1", "Truck A", "realm-1");
            Vehicle v2 = createVehicle("v2", "Van B", "realm-2");

            pool.createConnection(v1, "client-1").get(3, TimeUnit.SECONDS);
            pool.createConnection(v2, "client-1").get(3, TimeUnit.SECONDS);
            assertEquals(2, pool.getTotalConnectionCount());

            pool.closeConnection("v1");
            assertEquals(1, pool.getTotalConnectionCount());
            assertNotNull(pool.getConnection("v2", "client-1"));

            pool.closeAllConnections();
        }

        @Test
        @DisplayName("getConnection() returns correct connection after creation")
        void getConnection_afterCreation_returnsConnection() throws Exception {
            Vehicle vehicle = createVehicle("v1", "Truck A", "realm-1");

            pool.createConnection(vehicle, "client-1").get(3, TimeUnit.SECONDS);

            WebSocketConnection conn = pool.getConnection("v1", "client-1");
            assertNotNull(conn);
            assertEquals("v1", conn.getVehicle().getId());
            assertEquals("client-1", conn.getClientId());

            pool.closeAllConnections();
        }

        @Test
        @DisplayName("Multiple connections for different vehicles are tracked independently")
        void multipleConnections_trackedIndependently() throws Exception {
            Vehicle v1 = createVehicle("v1", "Truck A", "realm-1");
            Vehicle v2 = createVehicle("v2", "Van B", "realm-1");
            Vehicle v3 = createVehicle("v3", "Bus C", "realm-2");

            pool.createConnection(v1, "client-1").get(3, TimeUnit.SECONDS);
            pool.createConnection(v2, "client-1").get(3, TimeUnit.SECONDS);
            pool.createConnection(v3, "client-1").get(3, TimeUnit.SECONDS);

            assertEquals(3, pool.getTotalConnectionCount());

            assertNotNull(pool.getConnection("v1", "client-1"));
            assertNotNull(pool.getConnection("v2", "client-1"));
            assertNotNull(pool.getConnection("v3", "client-1"));

            pool.closeAllConnections();
        }
    }

    // ========================================================================
    // LocationDataHandler Integration Tests
    // ========================================================================

    @Nested
    @DisplayName("LocationDataHandler Integration Tests")
    class LocationDataHandlerIntegrationTests {

        @Test
        @DisplayName("13. parse() with valid JSON returns LocationData")
        void parse_validJson_returnsLocationData() {
            String json = """
                    {
                        "vehicleId": "v1",
                        "latitude": 38.2466,
                        "longitude": 21.7346,
                        "timestamp": 1704067200000,
                        "metadata": {"speed": 65.0, "heading": 180}
                    }
                    """;

            LocationData result = dataHandler.parse(json);

            assertNotNull(result);
            assertEquals("v1", result.getVehicleId());
            assertEquals(38.2466, result.getLatitude(), 0.0001);
            assertEquals(21.7346, result.getLongitude(), 0.0001);
            assertEquals(Instant.ofEpochMilli(1704067200000L), result.getTimestamp());
            assertEquals(65.0, result.getMetadata().get("speed"));
            assertEquals(180L, result.getMetadata().get("heading"));
        }

        @Test
        @DisplayName("14. parse() with invalid JSON returns null")
        void parse_invalidJson_returnsNull() {
            assertNull(dataHandler.parse("{broken json content"));
            assertNull(dataHandler.parse(""));
            assertNull(dataHandler.parse(null));
            assertNull(dataHandler.parse("not json at all"));
        }

        @Test
        @DisplayName("15. notifyListeners() calls all registered listeners")
        void notifyListeners_callsAllRegisteredListeners() {
            List<LocationData> listener1Data = new ArrayList<>();
            List<LocationData> listener2Data = new ArrayList<>();
            AtomicInteger listener3Count = new AtomicInteger(0);

            dataHandler.addListener(listener1Data::add);
            dataHandler.addListener(listener2Data::add);
            dataHandler.addListener(d -> listener3Count.incrementAndGet());

            LocationData data = new LocationData("v1", 37.0, 23.0, Instant.now(), null);
            dataHandler.notifyListeners(data);

            assertEquals(1, listener1Data.size());
            assertEquals(1, listener2Data.size());
            assertEquals(1, listener3Count.get());
            assertSame(data, listener1Data.get(0));
            assertSame(data, listener2Data.get(0));
        }

        @Test
        @DisplayName("notifyListeners() with throwing listener still notifies others")
        void notifyListeners_throwingListener_stillNotifiesOthers() {
            List<LocationData> received = new ArrayList<>();

            dataHandler.addListener(d -> { throw new RuntimeException("Listener failure"); });
            dataHandler.addListener(received::add);

            LocationData data = new LocationData("v1", 37.0, 23.0, Instant.now(), null);
            dataHandler.notifyListeners(data);

            assertEquals(1, received.size());
            assertSame(data, received.get(0));
        }

        @Test
        @DisplayName("parse() correctly handles coordinates at boundaries")
        void parse_boundaryCoordinates_parsesCorrectly() {
            String json = """
                    {
                        "vehicleId": "boundary-test",
                        "latitude": -90.0,
                        "longitude": 180.0,
                        "timestamp": 1704067200000
                    }
                    """;

            LocationData result = dataHandler.parse(json);

            assertNotNull(result);
            assertEquals(-90.0, result.getLatitude(), 0.0001);
            assertEquals(180.0, result.getLongitude(), 0.0001);
        }

        @Test
        @DisplayName("parse() rejects out-of-range coordinates")
        void parse_outOfRangeCoordinates_returnsNull() {
            String json1 = """
                    {"vehicleId": "v1", "latitude": 91.0, "longitude": 23.0}
                    """;
            String json2 = """
                    {"vehicleId": "v1", "latitude": 37.0, "longitude": 181.0}
                    """;

            assertNull(dataHandler.parse(json1));
            assertNull(dataHandler.parse(json2));
        }
    }
}
