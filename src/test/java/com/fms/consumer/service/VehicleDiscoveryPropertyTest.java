package com.fms.consumer.service;

import com.fms.consumer.integration.OpenRemoteRestClient;
import com.fms.consumer.integration.dto.VehicleDTO;
import com.fms.consumer.model.Vehicle;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for vehicle discovery per realm in {@link DiscoveryService}.
 *
 * <p><b>Validates: Requirements 3.1</b></p>
 *
 * <p>Property 6: Vehicle Discovery Per Realm —
 * "For any discovered realm, the system SHALL retrieve all associated vehicles from the API."</p>
 */
class VehicleDiscoveryPropertyTest {

    private AuthenticationService authService;
    private OpenRemoteRestClient restClient;
    private ConfigurationService configService;

    @BeforeProperty
    void setUp() {
        authService = mock(AuthenticationService.class);
        restClient = mock(OpenRemoteRestClient.class);
        configService = mock(ConfigurationService.class);

        when(authService.getSessionToken()).thenReturn("test-session-token");
    }

    /**
     * For any realm ID and any set of vehicles returned by the API,
     * discoverVehicles(realmId) returns ALL of them.
     *
     * <p><b>Validates: Requirements 3.1</b></p>
     */
    @Property(tries = 100)
    void discoverVehicles_returnsAllVehiclesFromApi(
            @ForAll("realmIds") String realmId,
            @ForAll("vehicleDtoLists") List<VehicleDTO> vehicleDTOs) throws Exception {

        when(restClient.getVehicles(eq(realmId), anyString()))
                .thenReturn(CompletableFuture.completedFuture(vehicleDTOs));

        DiscoveryService service = new DiscoveryService(authService, restClient, configService);
        List<Vehicle> result = service.discoverVehicles(realmId).get(5, TimeUnit.SECONDS);

        assertEquals(vehicleDTOs.size(), result.size(),
                "discoverVehicles must return all vehicles from the API response. " +
                        "Expected " + vehicleDTOs.size() + " but got " + result.size());
    }

    /**
     * The returned vehicle count equals the API response count.
     *
     * <p><b>Validates: Requirements 3.1</b></p>
     */
    @Property(tries = 100)
    void discoverVehicles_countMatchesApiResponse(
            @ForAll("realmIds") String realmId,
            @ForAll("vehicleDtoLists") List<VehicleDTO> vehicleDTOs) throws Exception {

        when(restClient.getVehicles(eq(realmId), anyString()))
                .thenReturn(CompletableFuture.completedFuture(vehicleDTOs));

        DiscoveryService service = new DiscoveryService(authService, restClient, configService);
        List<Vehicle> result = service.discoverVehicles(realmId).get(5, TimeUnit.SECONDS);

        assertEquals(vehicleDTOs.size(), result.size(),
                "Vehicle count must match the API response count");
    }

    /**
     * Each vehicle's id and name match what the API returned.
     *
     * <p><b>Validates: Requirements 3.1</b></p>
     */
    @Property(tries = 100)
    void discoverVehicles_idAndNameMatchApiResponse(
            @ForAll("realmIds") String realmId,
            @ForAll("vehicleDtoLists") List<VehicleDTO> vehicleDTOs) throws Exception {

        when(restClient.getVehicles(eq(realmId), anyString()))
                .thenReturn(CompletableFuture.completedFuture(vehicleDTOs));

        DiscoveryService service = new DiscoveryService(authService, restClient, configService);
        List<Vehicle> result = service.discoverVehicles(realmId).get(5, TimeUnit.SECONDS);

        for (int i = 0; i < vehicleDTOs.size(); i++) {
            VehicleDTO dto = vehicleDTOs.get(i);
            Vehicle vehicle = result.get(i);

            assertEquals(dto.getId(), vehicle.getId(),
                    "Vehicle id at index " + i + " must match the API response");
            assertEquals(dto.getName(), vehicle.getName(),
                    "Vehicle name at index " + i + " must match the API response");
        }
    }

    /**
     * The realmId is correctly set on each returned Vehicle model.
     * When the DTO has a non-null realmId, it uses the DTO's realmId;
     * otherwise it falls back to the provided realmId parameter.
     *
     * <p><b>Validates: Requirements 3.1</b></p>
     */
    @Property(tries = 100)
    void discoverVehicles_realmIdSetCorrectlyOnEachVehicle(
            @ForAll("realmIds") String realmId,
            @ForAll("vehicleDtoLists") List<VehicleDTO> vehicleDTOs) throws Exception {

        when(restClient.getVehicles(eq(realmId), anyString()))
                .thenReturn(CompletableFuture.completedFuture(vehicleDTOs));

        DiscoveryService service = new DiscoveryService(authService, restClient, configService);
        List<Vehicle> result = service.discoverVehicles(realmId).get(5, TimeUnit.SECONDS);

        for (int i = 0; i < vehicleDTOs.size(); i++) {
            VehicleDTO dto = vehicleDTOs.get(i);
            Vehicle vehicle = result.get(i);

            String expectedRealmId = dto.getRealmId() != null ? dto.getRealmId() : realmId;
            assertEquals(expectedRealmId, vehicle.getRealmId(),
                    "Vehicle realmId at index " + i + " must be set correctly. " +
                            "DTO realmId=" + dto.getRealmId() + ", parameter realmId=" + realmId);
        }
    }

    // --- Generators ---

    /**
     * Generates realm IDs of varying formats.
     */
    @Provide
    Arbitrary<String> realmIds() {
        return Arbitraries.of(
                "realm-1",
                "realm-alpha",
                "production",
                "staging-env",
                "fleet-management",
                "test-realm-001",
                "eu-west-1",
                "us-east-2",
                "alamanos",
                "master",
                "custom-realm",
                "realm_with_underscores",
                "R3alm-W1th-Numb3rs"
        );
    }

    /**
     * Generates lists of VehicleDTOs with varying sizes (0-15) and various
     * id/name/realmId combinations.
     */
    @Provide
    Arbitrary<List<VehicleDTO>> vehicleDtoLists() {
        Arbitrary<VehicleDTO> vehicleDtoArbitrary = Combinators.combine(
                vehicleIds(),
                vehicleNames(),
                vehicleRealmIds()
        ).as(VehicleDTO::new);

        return vehicleDtoArbitrary.list().ofMinSize(0).ofMaxSize(15);
    }

    private Arbitrary<String> vehicleIds() {
        return Arbitraries.of(
                "v-001", "v-002", "v-003", "v-004", "v-005",
                "vehicle-abc", "vehicle-def", "vehicle-ghi",
                "truck-100", "truck-200", "truck-300",
                "bus-A1", "bus-B2", "bus-C3",
                "asset-x1y2z3"
        );
    }

    private Arbitrary<String> vehicleNames() {
        return Arbitraries.of(
                "Fleet Truck 1", "Fleet Truck 2", "Fleet Truck 3",
                "City Bus Alpha", "City Bus Beta", "City Bus Gamma",
                "Delivery Van A", "Delivery Van B",
                "Heavy Loader X", "Light Cargo Y",
                "Emergency Vehicle", "Transport Unit 42",
                "Patrol Car", "Service Van", "Tanker"
        );
    }

    private Arbitrary<String> vehicleRealmIds() {
        // Include null to test the fallback behavior
        return Arbitraries.of(
                "realm-1", "realm-alpha", "production",
                "staging-env", "fleet-management", "eu-west-1",
                null, null, null
        );
    }
}
