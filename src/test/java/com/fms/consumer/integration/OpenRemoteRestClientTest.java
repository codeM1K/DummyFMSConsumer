package com.fms.consumer.integration;

import com.fms.consumer.config.OpenRemoteProperties;
import com.fms.consumer.integration.dto.AuthResponse;
import com.fms.consumer.integration.dto.RealmDTO;
import com.fms.consumer.integration.dto.VehicleDTO;
import com.fms.consumer.service.ConfigurationService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OpenRemoteRestClient}.
 * Uses MockWebServer to simulate the Open Remote API (OAuth2/Keycloak).
 */
class OpenRemoteRestClientTest {

    private MockWebServer mockServer;
    private OpenRemoteRestClient client;
    private ConfigurationService configurationService;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        String baseUrl = mockServer.url("").toString();
        // Remove trailing slash
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getApi().setEndpoint(baseUrl);
        properties.getConnection().setTimeout(5000);
        configurationService = new ConfigurationService(properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    // --- authenticate() success tests ---

    @Test
    void authenticate_withValidCredentials_returnsAuthResponse() throws Exception {
        String responseBody = """
                {
                    "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test-token",
                    "expires_in": 300,
                    "refresh_expires_in": 1800,
                    "token_type": "Bearer"
                }
                """;
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        client = new OpenRemoteRestClient(configurationService);

        CompletableFuture<AuthResponse> future = client.authenticate("test-client-id", "test-client-secret");
        AuthResponse response = future.get();

        assertNotNull(response);
        assertEquals("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test-token", response.getSessionToken());
        assertTrue(response.isSuccess());
        assertNull(response.getErrorMessage());
    }

    // --- authenticate() error tests ---

    @Test
    void authenticate_withInvalidCredentials_throwsRuntimeException() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\": \"unauthorized_client\"}"));

        client = new OpenRemoteRestClient(configurationService);

        CompletableFuture<AuthResponse> future = client.authenticate("wrong-client", "wrong-secret");

        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(RuntimeException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Authentication failed"));
        assertTrue(exception.getCause().getMessage().contains("401"));
    }

    @Test
    void authenticate_withServerError_throwsRuntimeException() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\": \"Internal Server Error\"}"));

        client = new OpenRemoteRestClient(configurationService);

        CompletableFuture<AuthResponse> future = client.authenticate("test-client-id", "test-client-secret");

        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(RuntimeException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Authentication failed"));
        assertTrue(exception.getCause().getMessage().contains("500"));
    }

    // --- getRealms() success tests ---

    @Test
    void getRealms_withValidToken_returnsRealmList() throws Exception {
        String responseBody = """
                [
                    {"name": "realm-1", "displayName": "Athens Fleet"},
                    {"name": "realm-2", "displayName": "Thessaloniki Fleet"}
                ]
                """;
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        client = new OpenRemoteRestClient(configurationService);

        CompletableFuture<List<RealmDTO>> future = client.getRealms("valid-access-token");
        List<RealmDTO> realms = future.get();

        assertNotNull(realms);
        assertEquals(2, realms.size());
        assertEquals("realm-1", realms.get(0).getId());
        assertEquals("Athens Fleet", realms.get(0).getName());
        assertEquals("realm-2", realms.get(1).getId());
        assertEquals("Thessaloniki Fleet", realms.get(1).getName());
    }

    // --- getRealms() error tests ---

    @Test
    void getRealms_withInvalidToken_throwsRuntimeException() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\": \"Forbidden\"}"));

        client = new OpenRemoteRestClient(configurationService);

        CompletableFuture<List<RealmDTO>> future = client.getRealms("invalid-token");

        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(RuntimeException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Realm retrieval failed"));
        assertTrue(exception.getCause().getMessage().contains("403"));
    }

    // --- getVehicles() success tests ---

    @Test
    void getVehicles_withValidRealm_returnsVehicleList() throws Exception {
        String responseBody = """
                [
                    {"id": "vehicle-1", "name": "Truck A", "realm": "realm-1", "type": "CarAsset"},
                    {"id": "vehicle-2", "name": "Van B", "realm": "realm-1", "type": "CarAsset"}
                ]
                """;
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        client = new OpenRemoteRestClient(configurationService);

        CompletableFuture<List<VehicleDTO>> future = client.getVehicles("realm-1", "valid-access-token");
        List<VehicleDTO> vehicles = future.get();

        assertNotNull(vehicles);
        assertEquals(2, vehicles.size());
        assertEquals("vehicle-1", vehicles.get(0).getId());
        assertEquals("Truck A", vehicles.get(0).getName());
        assertEquals("realm-1", vehicles.get(0).getRealmId());
        assertEquals("vehicle-2", vehicles.get(1).getId());
        assertEquals("Van B", vehicles.get(1).getName());
    }

    // --- getVehicles() error tests ---

    @Test
    void getVehicles_withUnknownRealm_throwsRuntimeException() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\": \"Realm not found\"}"));

        client = new OpenRemoteRestClient(configurationService);

        CompletableFuture<List<VehicleDTO>> future = client.getVehicles("unknown-realm", "valid-token");

        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(RuntimeException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Vehicle retrieval failed"));
        assertTrue(exception.getCause().getMessage().contains("404"));
    }

    // --- Request format verification tests ---

    @Test
    void authenticate_sendsFormUrlEncodedBody() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"access_token\": \"token\", \"expires_in\": 300, \"token_type\": \"Bearer\"}"));

        client = new OpenRemoteRestClient(configurationService);

        client.authenticate("test-client-id", "test-client-secret").get();

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("application/x-www-form-urlencoded", request.getHeader("Content-Type"));
        assertEquals("POST", request.getMethod());
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("grant_type=client_credentials"));
        assertTrue(body.contains("client_id=test-client-id"));
        assertTrue(body.contains("client_secret=test-client-secret"));
    }

    @Test
    void authenticate_sendsToCorrectEndpoint() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"access_token\": \"token\", \"expires_in\": 300, \"token_type\": \"Bearer\"}"));

        client = new OpenRemoteRestClient(configurationService);

        client.authenticate("test-client-id", "test-client-secret").get();

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("/auth/realms/master/protocol/openid-connect/token", request.getPath());
    }

    @Test
    void getRealms_sendsCorrectHeaders() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        client = new OpenRemoteRestClient(configurationService);

        client.getRealms("my-access-token").get();

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("Bearer my-access-token", request.getHeader("Authorization"));
        assertEquals("application/json", request.getHeader("Accept"));
        assertEquals("GET", request.getMethod());
        assertEquals("/api/master/realm", request.getPath());
    }

    @Test
    void getVehicles_sendsCorrectHeadersAndBody() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        client = new OpenRemoteRestClient(configurationService);

        client.getVehicles("realm-1", "my-access-token").get();

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("Bearer my-access-token", request.getHeader("Authorization"));
        assertEquals("application/json", request.getHeader("Content-Type"));
        assertEquals("application/json", request.getHeader("Accept"));
        assertEquals("POST", request.getMethod());
        assertEquals("/api/master/asset/query", request.getPath());
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"select\""));
        assertTrue(body.contains("\"include\""));
        assertTrue(body.contains("\"ALL\""));
        assertFalse(body.contains("\"realm\""), "Request body should not contain realm filter");
    }
}
