package com.fms.consumer.ui;

import com.fms.consumer.integration.LocationDataHandler;
import com.fms.consumer.model.ConsumptionMode;
import com.fms.consumer.service.AuthenticationService;
import com.fms.consumer.service.AuthenticationResult;
import com.fms.consumer.service.ConsumptionOrchestrator;
import com.fms.consumer.service.DiscoveryService;
import com.fms.consumer.service.LocationPollingService;
import com.fms.consumer.service.MetricsCollector;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application view for the Fleet Tracking Data Consumer.
 * Arranges UI components logically: controls at the top, realm/vehicle tree
 * on the left, location data grid in the center, and metrics at the bottom.
 * <p>
 * Injects all required services and coordinates initialization lifecycle:
 * authenticates on attach, starts discovery on success, and registers
 * location data listener for real-time grid updates.
 * <p>
 * Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 1.1, 1.3, 2.1, 2.4, 3.4
 */
@Route("")
public class MainView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(MainView.class);

    private final AuthenticationService authenticationService;
    private final DiscoveryService discoveryService;
    private final ConsumptionOrchestrator consumptionOrchestrator;
    private final MetricsCollector metricsCollector;
    private final LocationDataHandler locationDataHandler;
    private final LocationPollingService locationPollingService;

    private final RealmVehicleTree realmVehicleTree;
    private final ConsumptionControlPanel consumptionControlPanel;
    private final LocationDataGrid locationDataGrid;
    private final MetricsPanel metricsPanel;
    private final MultiClientConfigPanel multiClientConfigPanel;

    /**
     * Constructs the MainView with all required services injected by Spring.
     */
    public MainView(AuthenticationService authenticationService,
                    DiscoveryService discoveryService,
                    ConsumptionOrchestrator consumptionOrchestrator,
                    MetricsCollector metricsCollector,
                    LocationDataHandler locationDataHandler,
                    LocationPollingService locationPollingService) {
        this.authenticationService = authenticationService;
        this.discoveryService = discoveryService;
        this.consumptionOrchestrator = consumptionOrchestrator;
        this.metricsCollector = metricsCollector;
        this.locationDataHandler = locationDataHandler;
        this.locationPollingService = locationPollingService;

        // Create UI components
        this.realmVehicleTree = new RealmVehicleTree();
        this.consumptionControlPanel = new ConsumptionControlPanel();
        this.locationDataGrid = new LocationDataGrid();
        this.metricsPanel = new MetricsPanel();
        this.multiClientConfigPanel = new MultiClientConfigPanel();

        // Wire components to services
        consumptionControlPanel.setOrchestrator(consumptionOrchestrator);
        consumptionControlPanel.setVehicleSupplier(realmVehicleTree::getSelectedVehicles);
        consumptionControlPanel.setLocationPollingService(locationPollingService);
        consumptionControlPanel.setMetricsPanel(metricsPanel);
        multiClientConfigPanel.setOrchestrator(consumptionOrchestrator);
        metricsPanel.setMetricsCollector(metricsCollector);
        metricsPanel.setLocationPollingService(locationPollingService);

        // Register realm/vehicle tree as discovery listener
        discoveryService.addListener(realmVehicleTree);

        // Register location data grid as a location data listener
        locationDataHandler.addListener(locationDataGrid);

        // Build layout
        buildLayout();

        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    /**
     * Builds the main layout with controls on top, tree on left,
     * grid in center, and metrics at the bottom.
     */
    private void buildLayout() {
        // Top section: control panel and multi-client config side by side
        HorizontalLayout topControls = new HorizontalLayout(consumptionControlPanel, multiClientConfigPanel);
        topControls.setWidthFull();
        topControls.setSpacing(true);
        topControls.setPadding(false);

        // Middle section: tree on left, data grid in center
        SplitLayout middleSection = new SplitLayout(realmVehicleTree, locationDataGrid);
        middleSection.setSplitterPosition(30);
        middleSection.setSizeFull();

        // Bottom section: metrics panel
        metricsPanel.setWidthFull();

        add(topControls, middleSection, metricsPanel);
        setFlexGrow(1, middleSection);
    }

    /**
     * Called when the view is attached to the UI.
     * Initiates authentication and, on success, starts discovery and metrics refresh.
     */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        log.info("MainView attached, resetting state and initiating authentication");

        // Reset state on page load/refresh
        metricsCollector.reset();
        if (consumptionOrchestrator.getCurrentMode() != ConsumptionMode.IDLE) {
            if (consumptionOrchestrator.isRandomModeActive()) {
                consumptionOrchestrator.stopRandomMode();
            } else {
                consumptionOrchestrator.stopAllControlledSessions();
            }
        }
        locationPollingService.stop();
        locationPollingService.unsubscribeAll();
        consumptionControlPanel.refreshStatus();
        metricsPanel.stopTimer();

        UI ui = attachEvent.getUI();

        authenticationService.authenticate().whenComplete((result, throwable) -> {
            ui.access(() -> {
                if (throwable != null) {
                    handleAuthFailure("Authentication error: " + throwable.getMessage());
                    return;
                }

                if (result != null && result.isSuccess()) {
                    handleAuthSuccess();
                } else {
                    String errorMsg = result != null ? result.getErrorMessage() : "Unknown authentication failure";
                    handleAuthFailure(errorMsg);
                }
            });
        });
    }

    /**
     * Called when the view is detached from the UI.
     * Stops metrics auto-refresh and removes listeners.
     */
    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        log.info("MainView detached, cleaning up");
        metricsPanel.stopAutoRefresh();
        discoveryService.removeListener(realmVehicleTree);
        locationDataHandler.removeListener(locationDataGrid);
    }

    /**
     * Handles successful authentication by starting discovery and metrics.
     */
    private void handleAuthSuccess() {
        log.info("Authentication successful, starting discovery and metrics");

        Notification.show("Authentication successful", 3000, Notification.Position.BOTTOM_START);

        // Start initial realm discovery
        discoveryService.discoverRealms().whenComplete((realms, error) -> {
            if (error != null) {
                log.warn("Initial realm discovery failed: {}", error.getMessage());
            } else {
                log.info("Initial realm discovery completed with {} realms", realms.size());
            }
        });

        // Start periodic refresh
        discoveryService.startPeriodicRefresh();

        // Start metrics panel auto-refresh
        metricsPanel.startAutoRefresh();
    }

    /**
     * Handles authentication failure by displaying a notification to the user.
     */
    private void handleAuthFailure(String errorMessage) {
        log.error("Authentication failed: {}", errorMessage);
        Notification notification = Notification.show(
                "Authentication failed: " + errorMessage,
                5000,
                Notification.Position.MIDDLE
        );
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    // Package-private accessors for testing
    RealmVehicleTree getRealmVehicleTree() {
        return realmVehicleTree;
    }

    ConsumptionControlPanel getConsumptionControlPanel() {
        return consumptionControlPanel;
    }

    LocationDataGrid getLocationDataGrid() {
        return locationDataGrid;
    }

    MetricsPanel getMetricsPanel() {
        return metricsPanel;
    }

    MultiClientConfigPanel getMultiClientConfigPanel() {
        return multiClientConfigPanel;
    }
}
