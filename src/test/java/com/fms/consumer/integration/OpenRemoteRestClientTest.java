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
 * Uses MockWebServer to simulate the Open Remote API.
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
                    "sessionToken": "abc123-session-token",
                    "success": true,
                    "errorMessage": null
                }
                """;
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        client = new OpenRemoteRestClient(configurationService);

        CompletableFuture<AuthResponse> future = client.authenticate("alamanos-test", "hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc");
        AuthResponse response = future.get();

        assertNotNull(response);
        assertEquals("abc123-session-token", response.getSessionToken());
        assertTrue(response.isSuccess());
        assertNull(response.getErrorMessage());
    }

    // --- authenticate() error tests ---

    @Test
    void authenticate_withInvalidCredentials_throwsRuntimeException() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\": \"Invalid credentials\"}"));

        client = new OpenRemoteRestClient(configurationService);

        CompletableFuture<AuthResponse> future = client.authenticate("wrong-user", "wrong-token");

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

        CompletableFuture<AuthResponse> future = client.authenticate("alamanos-test", "hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc");

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
                    {"id": "realm-1", "name": "Athens Fleet"},
                    {"id": "realm-2", "name": "Thessaloniki Fleet"}
                ]
                """;
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        client = new OpenRemoteRestClient(configurationService);

        CompletableFuture<List<RealmDTO>> future = client.getRealms("valid-session-token");
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
                    {"id": "vehicle-1", "name": "Truck A", "realmId": "realm-1"},
                    {"id": "vehicle-2", "name": "Van B", "realmId": "realm-1"}
                ]
                """;
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        client = new OpenRemoteRestClient(configurationService);

        CompletableFuture<List<VehicleDTO>> future = client.getVehicles("realm-1", "valid-session-token");
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

    // --- Request header verification tests ---

    @Test
    void authenticate_sendsCorrectHeaders() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"sessionToken\": \"token\", \"success\": true, \"errorMessage\": null}"));

        client = new OpenRemoteRestClient(configurationService);

        client.authenticate("alamanos-test", "hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc").get();

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("application/json", request.getHeader("Content-Type"));
        assertEquals("application/json", request.getHeader("Accept"));
    }

    @Test
    void authenticate_sendsCorrectJsonBody() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"sessionToken\": \"token\", \"success\": true, \"errorMessage\": null}"));

        client = new OpenRemoteRestClient(configurationService);

        client.authenticate("alamanos-test", "hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc").get();

        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"username\""));
        assertTrue(body.contains("\"alamanos-test\""));
        assertTrue(body.contains("\"secret\""));
        assertTrue(body.contains("\"hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc\""));
    }

    @Test
    void getRealms_sendsCorrectHeaders() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        client = new OpenRemoteRestClient(configurationService);

        client.getRealms("session-token").get();

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("application/json", request.getHeader("Content-Type"));
        assertEquals("application/json", request.getHeader("Accept"));
        assertEquals("GET", request.getMethod());
    }

    @Test
    void getVehicles_sendsCorrectHeaders() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        client = new OpenRemoteRestClient(configurationService);

        client.getVehicles("realm-1", "session-token").get();

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("application/json", request.getHeader("Content-Type"));
        assertEquals("application/json", request.getHeader("Accept"));
        assertEquals("GET", request.getMethod());
    }
}
