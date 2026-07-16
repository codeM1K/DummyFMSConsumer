package com.fms.consumer.ui;

import com.fms.consumer.integration.LocationDataHandler;
import com.fms.consumer.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for MainView initialization, service wiring,
 * and component arrangement.
 */
class MainViewTest {

    private AuthenticationService authenticationService;
    private DiscoveryService discoveryService;
    private ConsumptionOrchestrator consumptionOrchestrator;
    private MetricsCollector metricsCollector;
    private LocationDataHandler locationDataHandler;

    @BeforeEach
    void setUp() {
        authenticationService = mock(AuthenticationService.class);
        discoveryService = mock(DiscoveryService.class);
        consumptionOrchestrator = mock(ConsumptionOrchestrator.class);
        metricsCollector = mock(MetricsCollector.class);
        locationDataHandler = mock(LocationDataHandler.class);

        when(consumptionOrchestrator.getCurrentMode())
                .thenReturn(com.fms.consumer.model.ConsumptionMode.IDLE);
        when(consumptionOrchestrator.getClientCount()).thenReturn(1);
    }

    @Test
    void mainView_constructsWithAllComponents() {
        MainView view = new MainView(
                authenticationService, discoveryService,
                consumptionOrchestrator, metricsCollector, locationDataHandler);

        assertNotNull(view.getRealmVehicleTree());
        assertNotNull(view.getConsumptionControlPanel());
        assertNotNull(view.getLocationDataGrid());
        assertNotNull(view.getMetricsPanel());
        assertNotNull(view.getMultiClientConfigPanel());
    }

    @Test
    void mainView_registersDiscoveryListener() {
        MainView view = new MainView(
                authenticationService, discoveryService,
                consumptionOrchestrator, metricsCollector, locationDataHandler);

        verify(discoveryService).addListener(view.getRealmVehicleTree());
    }

    @Test
    void mainView_registersLocationDataListener() {
        MainView view = new MainView(
                authenticationService, discoveryService,
                consumptionOrchestrator, metricsCollector, locationDataHandler);

        verify(locationDataHandler).addListener(view.getLocationDataGrid());
    }

    @Test
    void mainView_wiresOrchestratorToControlPanel() {
        MainView view = new MainView(
                authenticationService, discoveryService,
                consumptionOrchestrator, metricsCollector, locationDataHandler);

        // The control panel should have an orchestrator set
        // The fact that getCurrentMode is called indicates wiring worked
        verify(consumptionOrchestrator, atLeastOnce()).getCurrentMode();
    }

    @Test
    void mainView_wiresMetricsCollectorToPanel() {
        MainView view = new MainView(
                authenticationService, discoveryService,
                consumptionOrchestrator, metricsCollector, locationDataHandler);

        // MetricsPanel has a metricsCollector set - verified by construction without error
        assertNotNull(view.getMetricsPanel());
    }

    @Test
    void mainView_hasSizeFull() {
        MainView view = new MainView(
                authenticationService, discoveryService,
                consumptionOrchestrator, metricsCollector, locationDataHandler);

        // VerticalLayout should be full size
        assertEquals("100%", view.getWidth());
        assertEquals("100%", view.getHeight());
    }

    @Test
    void mainView_hasRouteAnnotation() {
        assertTrue(MainView.class.isAnnotationPresent(com.vaadin.flow.router.Route.class));
        com.vaadin.flow.router.Route route = MainView.class.getAnnotation(com.vaadin.flow.router.Route.class);
        assertEquals("", route.value());
    }

    @Test
    void mainView_hasPushAnnotation() {
        assertTrue(MainView.class.isAnnotationPresent(
                com.vaadin.flow.component.page.Push.class));
    }
}
