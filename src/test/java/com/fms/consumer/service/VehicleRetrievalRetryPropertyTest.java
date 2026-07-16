package com.fms.consumer.service;

import com.fms.consumer.integration.OpenRemoteRestClient;
import com.fms.consumer.integration.dto.VehicleDTO;
import com.fms.consumer.model.Vehicle;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for vehicle retrieval retry behavior.
 *
 * <p><b>Validates: Requirements 3.3</b></p>
 *
 * <p>Property 8: Vehicle Retrieval Retry —
 * "For any vehicle retrieval failure for a specific realm, the system SHALL log the error
 * and retry without affecting other realms."</p>
 */
class VehicleRetrievalRetryPropertyTest {

    private AuthenticationService authService;
    private OpenRemoteRestClient restClient;
    private ConfigurationService configService;

    @BeforeProperty
    void setUp() {
        authService = mock(AuthenticationService.class);
        restClient = mock(OpenRemoteRestClient.class);
        configService = mock(ConfigurationService.class);

        // Provide a valid session token
        when(authService.getSessionToken()).thenReturn("test-session-token");
        when(authService.authenticate()).thenReturn(
                CompletableFuture.completedFuture(AuthenticationResult.success("test-session-token")));

        // Configuration defaults
        when(configService.getRealmRefreshInterval()).thenReturn(60);
        when(configService.getVehicleRefreshInterval()).thenReturn(60);
    }

    /**
     * Property 1: For any vehicle retrieval failure for a realm, the failure is propagated
     * (CompletableFuture completes exceptionally).
     *
     * <p><b>Validates: Requirements 3.3</b></p>
     */
    @Property(tries = 30)
    void vehicleRetrievalFailure_propagatesException(@ForAll("realmIds") String realmId) {
        // Mock: getVehicles fails for the given realm
        when(restClient.getVehicles(eq(realmId), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("Vehicle retrieval failed for realm: " + realmId)));

        DiscoveryService discoveryService = new DiscoveryService(authService, restClient, configService);

        CompletableFuture<List<Vehicle>> future = discoveryService.discoverVehicles(realmId);

        // The future should complete exceptionally
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS),
                "discoverVehicles should propagate the failure for realm: " + realmId);

        assertNotNull(ex.getCause(), "Exception cause should not be null");
        assertTrue(ex.getCause() instanceof CompletionException,
                "Cause should be a CompletionException wrapping the original failure");

        discoveryService.shutdown();
    }

    /**
     * Property 2: A failure for one realm does NOT prevent discovery of vehicles for other realms.
     * This tests failure isolation between realms.
     *
     * <p><b>Validates: Requirements 3.3</b></p>
     */
    @Property(tries = 30)
    void failureForOneRealm_doesNotAffectOtherRealms(
            @ForAll("realmIds") String failingRealm,
            @ForAll("realmIds") String successRealm) {

        Assume.that(!failingRealm.equals(successRealm));

        // Mock: failing realm returns error
        when(restClient.getVehicles(eq(failingRealm), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("Simulated failure for realm: " + failingRealm)));

        // Mock: success realm returns vehicles
        List<VehicleDTO> vehicleDTOs = List.of(
                new VehicleDTO("v1", "Vehicle 1", successRealm),
                new VehicleDTO("v2", "Vehicle 2", successRealm)
        );
        when(restClient.getVehicles(eq(successRealm), anyString()))
                .thenReturn(CompletableFuture.completedFuture(vehicleDTOs));

        DiscoveryService discoveryService = new DiscoveryService(authService, restClient, configService);

        // First, trigger the failing realm discovery (it should fail)
        CompletableFuture<List<Vehicle>> failingFuture = discoveryService.discoverVehicles(failingRealm);
        assertThrows(ExecutionException.class,
                () -> failingFuture.get(5, TimeUnit.SECONDS),
                "Failing realm should propagate exception");

        // Now, the success realm should still work independently
        CompletableFuture<List<Vehicle>> successFuture = discoveryService.discoverVehicles(successRealm);
        List<Vehicle> vehicles = assertDoesNotThrow(
                () -> successFuture.get(5, TimeUnit.SECONDS),
                "Vehicle discovery for '" + successRealm + "' should succeed despite failure in '" + failingRealm + "'");

        assertEquals(2, vehicles.size(),
                "Should have retrieved 2 vehicles for the successful realm");
        assertTrue(vehicles.stream().allMatch(v -> successRealm.equals(v.getRealmId())),
                "All returned vehicles should belong to the successful realm");

        discoveryService.shutdown();
    }

    /**
     * Property 3: After a failure, a subsequent call for the same realm can succeed (retry capability).
     * This verifies that a transient failure does not permanently block vehicle discovery for a realm.
     *
     * <p><b>Validates: Requirements 3.3</b></p>
     */
    @Property(tries = 30)
    void afterFailure_subsequentCallForSameRealmCanSucceed(@ForAll("realmIds") String realmId) throws Exception {
        // First call fails, second call succeeds
        List<VehicleDTO> vehicleDTOs = List.of(
                new VehicleDTO("v1", "Vehicle 1", realmId),
                new VehicleDTO("v2", "Vehicle 2", realmId),
                new VehicleDTO("v3", "Vehicle 3", realmId)
        );

        when(restClient.getVehicles(eq(realmId), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("Transient failure for realm: " + realmId)))
                .thenReturn(CompletableFuture.completedFuture(vehicleDTOs));

        DiscoveryService discoveryService = new DiscoveryService(authService, restClient, configService);

        // First call should fail
        CompletableFuture<List<Vehicle>> firstAttempt = discoveryService.discoverVehicles(realmId);
        assertThrows(ExecutionException.class,
                () -> firstAttempt.get(5, TimeUnit.SECONDS),
                "First attempt should fail for realm: " + realmId);

        // Second call (retry) should succeed
        CompletableFuture<List<Vehicle>> retryAttempt = discoveryService.discoverVehicles(realmId);
        List<Vehicle> vehicles = retryAttempt.get(5, TimeUnit.SECONDS);

        assertNotNull(vehicles, "Retry attempt should return a non-null list");
        assertEquals(3, vehicles.size(),
                "Retry should successfully retrieve all 3 vehicles for realm: " + realmId);
        assertTrue(vehicles.stream().allMatch(v -> realmId.equals(v.getRealmId())),
                "All vehicles should belong to the retried realm");

        // Verify the REST client was called twice for this realm
        verify(restClient, times(2)).getVehicles(eq(realmId), anyString());

        discoveryService.shutdown();
    }

    /**
     * Provides arbitrary realm IDs for property-based testing.
     * Generates various realm ID formats to ensure the behavior holds for any realm identifier.
     */
    @Provide
    Arbitrary<String> realmIds() {
        return Arbitraries.of(
                "realm-alpha",
                "realm-beta",
                "realm-gamma",
                "realm-delta",
                "realm-epsilon",
                "production",
                "staging",
                "test-realm-01",
                "test-realm-02",
                "fleet-eu-west",
                "fleet-us-east",
                "fleet-apac",
                "master",
                "customer-a",
                "customer-b"
        );
    }
}
