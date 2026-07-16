package com.fms.consumer.service;

import com.fms.consumer.integration.OpenRemoteRestClient;
import com.fms.consumer.integration.dto.RealmDTO;
import com.fms.consumer.model.Realm;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for realm discovery after authentication in {@link DiscoveryService}.
 *
 * <p><b>Property 3: Realm Discovery After Authentication</b></p>
 * <p>For any successful authentication, the system SHALL retrieve the complete list
 * of available realms from the API.</p>
 *
 * <p><b>Validates: Requirements 2.1</b></p>
 */
class RealmDiscoveryPropertyTest {

    private AuthenticationService authService;
    private OpenRemoteRestClient restClient;
    private ConfigurationService configService;

    @BeforeProperty
    void setUp() {
        authService = mock(AuthenticationService.class);
        restClient = mock(OpenRemoteRestClient.class);
        configService = mock(ConfigurationService.class);

        // Simulate successful authentication: token is always available
        when(authService.getSessionToken()).thenReturn("test-session-token");
    }

    /**
     * For any set of realms returned by the API, discoverRealms() returns ALL of them
     * (no realms are lost).
     *
     * <p><b>Validates: Requirements 2.1</b></p>
     */
    @Property(tries = 100)
    void discoverRealms_returnsAllRealms_noRealmsLost(
            @ForAll("realmDTOLists") List<RealmDTO> apiRealms) throws Exception {

        when(restClient.getRealms(anyString()))
                .thenReturn(CompletableFuture.completedFuture(apiRealms));

        DiscoveryService discoveryService = new DiscoveryService(authService, restClient, configService);
        List<Realm> discoveredRealms = discoveryService.discoverRealms().get(5, TimeUnit.SECONDS);

        // Every realm from the API must appear in the result
        for (RealmDTO dto : apiRealms) {
            boolean found = discoveredRealms.stream()
                    .anyMatch(r -> r.getId().equals(dto.getId()));
            assertTrue(found, "Realm with id '" + dto.getId() + "' was lost during discovery");
        }
    }

    /**
     * For any successful authentication, discoverRealms() makes an API call with the session token.
     *
     * <p><b>Validates: Requirements 2.1</b></p>
     */
    @Property(tries = 100)
    void discoverRealms_callsApiWithSessionToken(
            @ForAll("realmDTOLists") List<RealmDTO> apiRealms) throws Exception {

        String sessionToken = "test-session-token";
        when(authService.getSessionToken()).thenReturn(sessionToken);
        when(restClient.getRealms(sessionToken))
                .thenReturn(CompletableFuture.completedFuture(apiRealms));

        DiscoveryService discoveryService = new DiscoveryService(authService, restClient, configService);
        discoveryService.discoverRealms().get(5, TimeUnit.SECONDS);

        // Use atLeastOnce() because the service may have internal retry/scheduling mechanisms
        verify(restClient, atLeastOnce()).getRealms(sessionToken);
    }

    /**
     * The returned realm list size equals the API response size.
     *
     * <p><b>Validates: Requirements 2.1</b></p>
     */
    @Property(tries = 100)
    void discoverRealms_returnsSameSizeAsApiResponse(
            @ForAll("realmDTOLists") List<RealmDTO> apiRealms) throws Exception {

        when(restClient.getRealms(anyString()))
                .thenReturn(CompletableFuture.completedFuture(apiRealms));

        DiscoveryService discoveryService = new DiscoveryService(authService, restClient, configService);
        List<Realm> discoveredRealms = discoveryService.discoverRealms().get(5, TimeUnit.SECONDS);

        assertEquals(apiRealms.size(), discoveredRealms.size(),
                "Discovered realm count must match API response count");
    }

    /**
     * Each realm's id and name match exactly what the API returned.
     *
     * <p><b>Validates: Requirements 2.1</b></p>
     */
    @Property(tries = 100)
    void discoverRealms_realmIdAndNameMatchApiResponse(
            @ForAll("realmDTOLists") List<RealmDTO> apiRealms) throws Exception {

        when(restClient.getRealms(anyString()))
                .thenReturn(CompletableFuture.completedFuture(apiRealms));

        DiscoveryService discoveryService = new DiscoveryService(authService, restClient, configService);
        List<Realm> discoveredRealms = discoveryService.discoverRealms().get(5, TimeUnit.SECONDS);

        for (int i = 0; i < apiRealms.size(); i++) {
            RealmDTO expected = apiRealms.get(i);
            Realm actual = discoveredRealms.get(i);
            assertEquals(expected.getId(), actual.getId(),
                    "Realm id at index " + i + " must match API response");
            assertEquals(expected.getName(), actual.getName(),
                    "Realm name at index " + i + " must match API response");
        }
    }

    /**
     * Provides arbitrary lists of RealmDTOs with varying sizes (0-20) and various
     * id/name combinations.
     */
    @Provide
    Arbitrary<List<RealmDTO>> realmDTOLists() {
        Arbitrary<String> ids = Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(1).ofMaxLength(20)
                .map(s -> "realm-" + s);

        Arbitrary<String> names = Arbitraries.strings()
                .alpha()
                .ofMinLength(1).ofMaxLength(30)
                .map(s -> "Realm " + s);

        Arbitrary<RealmDTO> realmDTOs = Combinators.combine(ids, names)
                .as(RealmDTO::new);

        return realmDTOs.list().ofMinSize(0).ofMaxSize(20);
    }
}
