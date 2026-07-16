package com.fms.consumer;

import com.fms.consumer.config.AuthenticationHealthIndicator;
import com.fms.consumer.config.GracefulShutdownConfig;
import com.fms.consumer.config.WebSocketHealthIndicator;
import com.fms.consumer.integration.LocationDataHandler;
import com.fms.consumer.integration.OpenRemoteRestClient;
import com.fms.consumer.integration.WebSocketClientPool;
import com.fms.consumer.service.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying that all application components can be instantiated
 * and wired correctly. These tests validate the dependency graph without requiring
 * a full Spring context (which would require Vaadin frontend build).
 */
class ApplicationStartupIntegrationTest {

    @Test
    void enableAsyncAnnotationPresent() {
        assertTrue(FleetTrackingConsumerApplication.class.isAnnotationPresent(
                org.springframework.scheduling.annotation.EnableAsync.class));
    }

    @Test
    void enableSchedulingAnnotationPresent() {
        assertTrue(FleetTrackingConsumerApplication.class.isAnnotationPresent(
                org.springframework.scheduling.annotation.EnableScheduling.class));
    }

    @Test
    void springBootApplicationAnnotationPresent() {
        assertTrue(FleetTrackingConsumerApplication.class.isAnnotationPresent(
                org.springframework.boot.autoconfigure.SpringBootApplication.class));
    }

    @Test
    void configurationPropertiesScanAnnotationPresent() {
        assertTrue(FleetTrackingConsumerApplication.class.isAnnotationPresent(
                org.springframework.boot.context.properties.ConfigurationPropertiesScan.class));
    }

    @Test
    void metricsCollectorCanBeInstantiated() {
        MetricsCollector collector = new MetricsCollector();
        assertNotNull(collector);
        assertEquals(0, collector.getActiveConnections());
        assertEquals(0, collector.getTotalUpdatesReceived());
        collector.shutdown();
    }

    @Test
    void locationDataHandlerCanBeInstantiated() {
        LocationDataHandler handler = new LocationDataHandler();
        assertNotNull(handler);
        assertEquals(0, handler.getListenerCount());
    }

    @Test
    void webSocketHealthIndicatorReportsStatus() {
        MetricsCollector collector = new MetricsCollector();
        WebSocketHealthIndicator indicator = new WebSocketHealthIndicator(collector);
        assertNotNull(indicator.health());
        assertEquals(org.springframework.boot.actuate.health.Status.UP,
                indicator.health().getStatus());
        collector.shutdown();
    }

    @Test
    void authenticationHealthIndicator_reportsDownWhenNotAuthenticated() {
        // Create a minimal auth service using mocks
        AuthenticationService authService = org.mockito.Mockito.mock(AuthenticationService.class);
        org.mockito.Mockito.when(authService.isAuthenticated()).thenReturn(false);

        AuthenticationHealthIndicator indicator = new AuthenticationHealthIndicator(authService);
        assertEquals(org.springframework.boot.actuate.health.Status.DOWN,
                indicator.health().getStatus());
    }

    @Test
    void authenticationHealthIndicator_reportsUpWhenAuthenticated() {
        AuthenticationService authService = org.mockito.Mockito.mock(AuthenticationService.class);
        org.mockito.Mockito.when(authService.isAuthenticated()).thenReturn(true);

        AuthenticationHealthIndicator indicator = new AuthenticationHealthIndicator(authService);
        assertEquals(org.springframework.boot.actuate.health.Status.UP,
                indicator.health().getStatus());
    }

    @Test
    void gracefulShutdownConfigCanBeInstantiated() {
        WebSocketClientPool pool = org.mockito.Mockito.mock(WebSocketClientPool.class);
        DiscoveryService discovery = org.mockito.Mockito.mock(DiscoveryService.class);

        GracefulShutdownConfig config = new GracefulShutdownConfig(pool, discovery);
        assertNotNull(config);

        // Verify shutdown handler calls expected methods
        config.onApplicationShutdown();
        org.mockito.Mockito.verify(pool).closeAllConnections();
        org.mockito.Mockito.verify(discovery).stopPeriodicRefresh();
    }
}
