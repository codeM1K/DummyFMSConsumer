package com.fms.consumer.service;

import com.fms.consumer.integration.OpenRemoteRestClient;
import com.fms.consumer.integration.dto.RealmDTO;
import com.fms.consumer.integration.dto.VehicleDTO;
import com.fms.consumer.model.Realm;
import com.fms.consumer.model.Vehicle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DiscoveryService}.
 * Tests periodic refresh, realm/vehicle discovery, error handling and retry logic,
 * caching, and listener notification.
 *
 * Requirements: 2.1, 2.3, 2.4, 3.1, 3.3, 3.4
 */
class DiscoveryServiceTest {

    @Mock
    private AuthenticationService authService;

    @Mock
    private OpenRemoteRestClient restClient;

    @Mock
    private ConfigurationService configService;

    private DiscoveryService discoveryService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        // Configure short intervals to keep tests fast
        when(configService.getRealmRefreshInterval()).thenReturn(60);
        when(configService.getVehicleRefreshInterval()).thenReturn(60);

        discoveryService = new DiscoveryService(authService, restClient, configService);
    }

    @AfterEach
    void tearDown() throws Exception {
        discoveryService.shutdown();
        mocks.close();
    }

    // --- 1. discoverRealms() with valid session token returns converted realm list ---

    @Test
    void discoverRealms_withValidSessionToken_returnsConvertedRealmList() throws Exception {
        when(authService.getSessionToken()).thenReturn("valid-token");

        List<RealmDTO> realmDTOs = List.of(
                new RealmDTO("realm-1", "Realm One"),
                new RealmDTO("realm-2", "Realm Two")
        );
        when(restClient.getRealms("valid-token"))
                .thenReturn(CompletableFuture.completedFuture(realmDTOs));

        List<Realm> result = discoveryService.discoverRealms().get(5, TimeUnit.SECONDS);

        assertEquals(2, result.size());
        assertEquals("realm-1", result.get(0).getId());
        assertEquals("Realm One", result.get(0).getName());
        assertEquals("realm-2", result.get(1).getId());
        assertEquals("Realm Two", result.get(1).getName());
    }

    // --- 2. discoverRealms() with no session token triggers authentication first ---

    @Test
    void discoverRealms_withNoSessionToken_triggersAuthenticationFirst() throws Exception {
        when(authService.getSessionToken()).thenReturn(null);
        when(authService.authenticate())
                .thenReturn(CompletableFuture.completedFuture(AuthenticationResult.success("new-token")));

        List<RealmDTO> realmDTOs = List.of(new RealmDTO("realm-1", "Realm One"));
        when(restClient.getRealms("new-token"))
                .thenReturn(CompletableFuture.completedFuture(realmDTOs));

        List<Realm> result = discoveryService.discoverRealms().get(5, TimeUnit.SECONDS);

        verify(authService).authenticate();
        assertEquals(1, result.size());
        assertEquals("realm-1", result.get(0).getId());
    }

    @Test
    void discoverRealms_withNoSessionToken_failsWhenAuthenticationFails() {
        when(authService.getSessionToken()).thenReturn(null);
        when(authService.authenticate())
                .thenReturn(CompletableFuture.completedFuture(AuthenticationResult.failure("Auth failed")));

        assertThrows(Exception.class, () ->
                discoveryService.discoverRealms().get(5, TimeUnit.SECONDS));
    }

    // --- 3. discoverVehicles() returns converted vehicle list with correct realmId ---

    @Test
    void discoverVehicles_returnsConvertedVehicleListWithCorrectRealmId() throws Exception {
        when(authService.getSessionToken()).thenReturn("valid-token");

        List<VehicleDTO> vehicleDTOs = List.of(
                new VehicleDTO("v-1", "Vehicle One", "realm-1"),
                new VehicleDTO("v-2", "Vehicle Two", null)
        );
        when(restClient.getVehicles("realm-1", "valid-token"))
                .thenReturn(CompletableFuture.completedFuture(vehicleDTOs));

        List<Vehicle> result = discoveryService.discoverVehicles("realm-1").get(5, TimeUnit.SECONDS);

        assertEquals(2, result.size());
        assertEquals("v-1", result.get(0).getId());
        assertEquals("Vehicle One", result.get(0).getName());
        assertEquals("realm-1", result.get(0).getRealmId());
        // When DTO has null realmId, uses the provided realmId parameter
        assertEquals("v-2", result.get(1).getId());
        assertEquals("realm-1", result.get(1).getRealmId());
    }

    // --- 4. getCachedRealms() returns cached data after discovery ---

    @Test
    void getCachedRealms_returnsEmptyList_beforeDiscovery() {
        List<Realm> cached = discoveryService.getCachedRealms();
        assertTrue(cached.isEmpty());
    }

    @Test
    void getCachedRealms_returnsCachedData_afterDiscovery() throws Exception {
        when(authService.getSessionToken()).thenReturn("valid-token");
        List<RealmDTO> realmDTOs = List.of(
                new RealmDTO("realm-1", "Realm One"),
                new RealmDTO("realm-2", "Realm Two")
        );
        when(restClient.getRealms("valid-token"))
                .thenReturn(CompletableFuture.completedFuture(realmDTOs));

        discoveryService.discoverRealms().get(5, TimeUnit.SECONDS);

        List<Realm> cached = discoveryService.getCachedRealms();
        assertEquals(2, cached.size());
    }

    // --- 5. getCachedVehicles() returns cached data for specific realm ---

    @Test
    void getCachedVehicles_returnsEmptyList_beforeDiscovery() {
        List<Vehicle> cached = discoveryService.getCachedVehicles("realm-1");
        assertTrue(cached.isEmpty());
    }

    @Test
    void getCachedVehicles_returnsCachedData_afterDiscovery() throws Exception {
        when(authService.getSessionToken()).thenReturn("valid-token");
        List<VehicleDTO> vehicleDTOs = List.of(
                new VehicleDTO("v-1", "Vehicle One", "realm-1")
        );
        when(restClient.getVehicles("realm-1", "valid-token"))
                .thenReturn(CompletableFuture.completedFuture(vehicleDTOs));

        discoveryService.discoverVehicles("realm-1").get(5, TimeUnit.SECONDS);

        List<Vehicle> cached = discoveryService.getCachedVehicles("realm-1");
        assertEquals(1, cached.size());
        assertEquals("v-1", cached.get(0).getId());
    }

    @Test
    void getCachedVehicles_returnsEmptyList_forUnknownRealm() throws Exception {
        when(authService.getSessionToken()).thenReturn("valid-token");
        List<VehicleDTO> vehicleDTOs = List.of(
                new VehicleDTO("v-1", "Vehicle One", "realm-1")
        );
        when(restClient.getVehicles("realm-1", "valid-token"))
                .thenReturn(CompletableFuture.completedFuture(vehicleDTOs));

        discoveryService.discoverVehicles("realm-1").get(5, TimeUnit.SECONDS);

        List<Vehicle> cached = discoveryService.getCachedVehicles("unknown-realm");
        assertTrue(cached.isEmpty());
    }

    // --- 6. Listener notification: onRealmsUpdated called after successful realm discovery ---

    @Test
    void listener_onRealmsUpdated_calledAfterSuccessfulRealmDiscovery() throws Exception {
        when(authService.getSessionToken()).thenReturn("valid-token");
        List<RealmDTO> realmDTOs = List.of(new RealmDTO("realm-1", "Realm One"));
        when(restClient.getRealms("valid-token"))
                .thenReturn(CompletableFuture.completedFuture(realmDTOs));

        AtomicReference<List<Realm>> notifiedRealms = new AtomicReference<>();
        discoveryService.addListener(new DiscoveryListener() {
            @Override
            public void onRealmsUpdated(List<Realm> realms) {
                notifiedRealms.set(realms);
            }
        });

        discoveryService.discoverRealms().get(5, TimeUnit.SECONDS);

        assertNotNull(notifiedRealms.get());
        assertEquals(1, notifiedRealms.get().size());
        assertEquals("realm-1", notifiedRealms.get().get(0).getId());
    }

    // --- 7. Listener notification: onVehiclesUpdated called after successful vehicle discovery ---

    @Test
    void listener_onVehiclesUpdated_calledAfterSuccessfulVehicleDiscovery() throws Exception {
        when(authService.getSessionToken()).thenReturn("valid-token");
        List<VehicleDTO> vehicleDTOs = List.of(new VehicleDTO("v-1", "Vehicle One", "realm-1"));
        when(restClient.getVehicles("realm-1", "valid-token"))
                .thenReturn(CompletableFuture.completedFuture(vehicleDTOs));

        AtomicReference<String> notifiedRealmId = new AtomicReference<>();
        AtomicReference<List<Vehicle>> notifiedVehicles = new AtomicReference<>();
        discoveryService.addListener(new DiscoveryListener() {
            @Override
            public void onVehiclesUpdated(String realmId, List<Vehicle> vehicles) {
                notifiedRealmId.set(realmId);
                notifiedVehicles.set(vehicles);
            }
        });

        discoveryService.discoverVehicles("realm-1").get(5, TimeUnit.SECONDS);

        assertEquals("realm-1", notifiedRealmId.get());
        assertNotNull(notifiedVehicles.get());
        assertEquals(1, notifiedVehicles.get().size());
        assertEquals("v-1", notifiedVehicles.get().get(0).getId());
    }

    // --- 8. Listener notification: onDiscoveryError called after failure ---

    @Test
    void listener_onDiscoveryError_calledAfterRealmDiscoveryFailure() {
        when(authService.getSessionToken()).thenReturn("valid-token");
        when(restClient.getRealms("valid-token"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("API error")));

        AtomicReference<Throwable> notifiedError = new AtomicReference<>();
        discoveryService.addListener(new DiscoveryListener() {
            @Override
            public void onDiscoveryError(Throwable error) {
                notifiedError.set(error);
            }
        });

        assertThrows(Exception.class, () ->
                discoveryService.discoverRealms().get(5, TimeUnit.SECONDS));

        assertNotNull(notifiedError.get());
    }

    @Test
    void listener_onDiscoveryError_calledAfterVehicleDiscoveryFailure() {
        when(authService.getSessionToken()).thenReturn("valid-token");
        when(restClient.getVehicles("realm-1", "valid-token"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("API error")));

        AtomicReference<Throwable> notifiedError = new AtomicReference<>();
        discoveryService.addListener(new DiscoveryListener() {
            @Override
            public void onDiscoveryError(Throwable error) {
                notifiedError.set(error);
            }
        });

        assertThrows(Exception.class, () ->
                discoveryService.discoverVehicles("realm-1").get(5, TimeUnit.SECONDS));

        assertNotNull(notifiedError.get());
    }

    // --- 9. startPeriodicRefresh() schedules tasks (verify they can run without error) ---

    @Test
    void startPeriodicRefresh_schedulesRealmAndVehicleRefreshTasks() throws Exception {
        // Use short intervals for the test
        when(configService.getRealmRefreshInterval()).thenReturn(1);
        when(configService.getVehicleRefreshInterval()).thenReturn(1);

        // Re-create with short intervals
        discoveryService.shutdown();
        discoveryService = new DiscoveryService(authService, restClient, configService);

        when(authService.getSessionToken()).thenReturn("valid-token");
        List<RealmDTO> realmDTOs = List.of(new RealmDTO("realm-1", "Realm One"));
        when(restClient.getRealms("valid-token"))
                .thenReturn(CompletableFuture.completedFuture(realmDTOs));

        discoveryService.startPeriodicRefresh();

        // Wait briefly for the scheduled task to fire at least once
        Thread.sleep(1500);

        // Verify getRealms was called at least once by the periodic task
        verify(restClient, atLeastOnce()).getRealms("valid-token");
    }

    // --- 10. stopPeriodicRefresh() cancels scheduled tasks ---

    @Test
    void stopPeriodicRefresh_cancelsScheduledTasks() throws Exception {
        when(configService.getRealmRefreshInterval()).thenReturn(1);
        when(configService.getVehicleRefreshInterval()).thenReturn(1);

        discoveryService.shutdown();
        discoveryService = new DiscoveryService(authService, restClient, configService);

        when(authService.getSessionToken()).thenReturn("valid-token");
        when(restClient.getRealms("valid-token"))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        discoveryService.startPeriodicRefresh();

        // Stop the periodic refresh immediately
        discoveryService.stopPeriodicRefresh();

        // Reset mock call counts
        reset(restClient);
        when(restClient.getRealms("valid-token"))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        // Wait to confirm no further calls happen after stop
        Thread.sleep(1500);

        // After stopping, no further realm discovery calls should occur
        verify(restClient, never()).getRealms(anyString());
    }

    // --- 11. Discovery failure triggers retry path ---

    @Test
    void discoverRealms_canSucceedAfterFailure() throws Exception {
        when(authService.getSessionToken()).thenReturn("valid-token");

        // First call fails
        when(restClient.getRealms("valid-token"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Temporary failure")));

        assertThrows(Exception.class, () ->
                discoveryService.discoverRealms().get(5, TimeUnit.SECONDS));

        // Second call succeeds
        List<RealmDTO> realmDTOs = List.of(new RealmDTO("realm-1", "Realm One"));
        when(restClient.getRealms("valid-token"))
                .thenReturn(CompletableFuture.completedFuture(realmDTOs));

        List<Realm> result = discoveryService.discoverRealms().get(5, TimeUnit.SECONDS);

        assertEquals(1, result.size());
        assertEquals("realm-1", result.get(0).getId());
    }

    // --- Additional edge case tests ---

    @Test
    void addListener_withNull_doesNotThrow() {
        assertDoesNotThrow(() -> discoveryService.addListener(null));
    }

    @Test
    void removeListener_stopsNotifications() throws Exception {
        when(authService.getSessionToken()).thenReturn("valid-token");
        List<RealmDTO> realmDTOs = List.of(new RealmDTO("realm-1", "Realm One"));
        when(restClient.getRealms("valid-token"))
                .thenReturn(CompletableFuture.completedFuture(realmDTOs));

        AtomicReference<List<Realm>> notifiedRealms = new AtomicReference<>();
        DiscoveryListener listener = new DiscoveryListener() {
            @Override
            public void onRealmsUpdated(List<Realm> realms) {
                notifiedRealms.set(realms);
            }
        };

        discoveryService.addListener(listener);
        discoveryService.removeListener(listener);

        discoveryService.discoverRealms().get(5, TimeUnit.SECONDS);

        assertNull(notifiedRealms.get());
    }

    @Test
    void listener_exceptionDoesNotAffectOtherListeners() throws Exception {
        when(authService.getSessionToken()).thenReturn("valid-token");
        List<RealmDTO> realmDTOs = List.of(new RealmDTO("realm-1", "Realm One"));
        when(restClient.getRealms("valid-token"))
                .thenReturn(CompletableFuture.completedFuture(realmDTOs));

        AtomicReference<List<Realm>> secondListenerNotified = new AtomicReference<>();

        // First listener throws exception
        discoveryService.addListener(new DiscoveryListener() {
            @Override
            public void onRealmsUpdated(List<Realm> realms) {
                throw new RuntimeException("Listener error");
            }
        });

        // Second listener should still be notified
        discoveryService.addListener(new DiscoveryListener() {
            @Override
            public void onRealmsUpdated(List<Realm> realms) {
                secondListenerNotified.set(realms);
            }
        });

        discoveryService.discoverRealms().get(5, TimeUnit.SECONDS);

        assertNotNull(secondListenerNotified.get());
        assertEquals(1, secondListenerNotified.get().size());
    }
}
