package com.fms.consumer.ui;

import com.fms.consumer.model.*;
import com.fms.consumer.service.ConsumptionOrchestrator;
import com.fms.consumer.service.MetricsCollector;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for UI component initialization, button handlers, and data binding.
 *
 * <p>Tests cover: RealmVehicleTree, ConsumptionControlPanel, LocationDataGrid,
 * MetricsPanel, and MultiClientConfigPanel.</p>
 *
 * <p><b>Requirements: 10.2, 10.3, 10.4, 10.5, 10.6</b></p>
 */
class UIComponentsUnitTest {

    // === RealmVehicleTree Tests (Req 10.2) ===

    @Test
    void realmVehicleTree_initialization_createsEmptyTree() {
        RealmVehicleTree tree = new RealmVehicleTree();

        assertNotNull(tree.getTreeGrid(), "TreeGrid must be initialized");
        assertTrue(tree.getSelectedVehicles().isEmpty(), "No vehicles selected initially");
        assertTrue(tree.getTreeGrid().getColumns().size() >= 3,
                "TreeGrid must have at least 3 columns (Name, Type, Status)");
    }

    @Test
    void realmVehicleTree_setData_populatesHierarchy() {
        RealmVehicleTree tree = new RealmVehicleTree();
        Vehicle v1 = new Vehicle("v1", "Truck 1", "r1", VehicleStatus.ACTIVE);
        Vehicle v2 = new Vehicle("v2", "Truck 2", "r1", VehicleStatus.INACTIVE);
        Realm realm = new Realm("r1", "Fleet A", List.of(v1, v2));

        tree.setData(List.of(realm));

        // Tree should have realm as root with 2 vehicle children
        var dataProvider = (com.vaadin.flow.data.provider.hierarchy.TreeDataProvider<RealmVehicleTree.TreeNode>)
                tree.getTreeGrid().getDataProvider();
        var treeData = dataProvider.getTreeData();

        assertEquals(1, treeData.getRootItems().size(), "One root realm node");
        var rootNode = treeData.getRootItems().get(0);
        assertTrue(rootNode.isRealm());
        assertEquals(2, treeData.getChildren(rootNode).size(), "Two vehicle children");
    }

    @Test
    void realmVehicleTree_setDataMultipleTimes_replacesData() {
        RealmVehicleTree tree = new RealmVehicleTree();

        Realm realm1 = new Realm("r1", "Fleet A", List.of(
                new Vehicle("v1", "Truck 1", "r1", VehicleStatus.ACTIVE)));
        tree.setData(List.of(realm1));

        Realm realm2 = new Realm("r2", "Fleet B", List.of(
                new Vehicle("v2", "Van 1", "r2", VehicleStatus.ACTIVE),
                new Vehicle("v3", "Van 2", "r2", VehicleStatus.ACTIVE)));
        tree.setData(List.of(realm2));

        var dataProvider = (com.vaadin.flow.data.provider.hierarchy.TreeDataProvider<RealmVehicleTree.TreeNode>)
                tree.getTreeGrid().getDataProvider();
        var treeData = dataProvider.getTreeData();

        assertEquals(1, treeData.getRootItems().size());
        assertEquals("r2", treeData.getRootItems().get(0).getId(),
                "Data should be replaced with new realm");
    }

    @Test
    void realmVehicleTree_implementsDiscoveryListener() {
        RealmVehicleTree tree = new RealmVehicleTree();
        assertTrue(tree instanceof com.fms.consumer.service.DiscoveryListener,
                "RealmVehicleTree must implement DiscoveryListener for real-time updates");
    }

    // === ConsumptionControlPanel Tests (Req 10.3, 10.4) ===

    @Test
    void consumptionControlPanel_initialization_correctButtonStates() {
        ConsumptionControlPanel panel = new ConsumptionControlPanel();

        assertTrue(panel.getStartRandomButton().isEnabled(), "Start Random enabled initially");
        assertFalse(panel.getStopRandomButton().isEnabled(), "Stop Random disabled initially");
        assertTrue(panel.getStartControlledButton().isEnabled(), "Start Controlled enabled initially");
        assertFalse(panel.getStopControlledButton().isEnabled(), "Stop Controlled disabled initially");
        assertEquals("Mode: IDLE", panel.getModeStatusLabel().getText());
    }

    @Test
    void consumptionControlPanel_startRandomButton_invokesOrchestrator() {
        ConsumptionControlPanel panel = new ConsumptionControlPanel();
        ConsumptionOrchestrator orchestrator = mock(ConsumptionOrchestrator.class);
        when(orchestrator.getCurrentMode()).thenReturn(ConsumptionMode.IDLE)
                .thenReturn(ConsumptionMode.RANDOM);

        panel.setOrchestrator(orchestrator);
        panel.getStartRandomButton().click();

        verify(orchestrator).startRandomMode();
    }

    @Test
    void consumptionControlPanel_stopRandomButton_invokesOrchestrator() {
        ConsumptionControlPanel panel = new ConsumptionControlPanel();
        ConsumptionOrchestrator orchestrator = mock(ConsumptionOrchestrator.class);
        when(orchestrator.getCurrentMode()).thenReturn(ConsumptionMode.RANDOM)
                .thenReturn(ConsumptionMode.IDLE);

        panel.setOrchestrator(orchestrator);
        panel.getStopRandomButton().click();

        verify(orchestrator).stopRandomMode();
    }

    @Test
    void consumptionControlPanel_startControlledButton_passesSelectedVehicles() {
        ConsumptionControlPanel panel = new ConsumptionControlPanel();
        ConsumptionOrchestrator orchestrator = mock(ConsumptionOrchestrator.class);
        when(orchestrator.getCurrentMode()).thenReturn(ConsumptionMode.IDLE)
                .thenReturn(ConsumptionMode.CONTROLLED);

        Set<Vehicle> vehicles = new HashSet<>();
        vehicles.add(new Vehicle("v1", "Truck", "r1", VehicleStatus.ACTIVE));
        panel.setVehicleSupplier(() -> vehicles);
        panel.setOrchestrator(orchestrator);

        panel.getStartControlledButton().click();

        verify(orchestrator).startControlledMode(vehicles);
    }

    @Test
    void consumptionControlPanel_withoutOrchestrator_buttonsDoNotThrow() {
        ConsumptionControlPanel panel = new ConsumptionControlPanel();

        assertDoesNotThrow(() -> panel.getStartRandomButton().click());
        assertDoesNotThrow(() -> panel.getStopRandomButton().click());
        assertDoesNotThrow(() -> panel.getStartControlledButton().click());
        assertDoesNotThrow(() -> panel.getStopControlledButton().click());
    }

    // === LocationDataGrid Tests (Req 10.5) ===

    @Test
    void locationDataGrid_initialization_emptyGrid() {
        LocationDataGrid grid = new LocationDataGrid();

        assertEquals(0, grid.getRowCount());
        assertTrue(grid.getRows().isEmpty());
        assertNotNull(grid.getGrid());
        assertEquals(7, grid.getGrid().getColumns().size(),
                "Grid must have 7 columns: ID, Name, Realm, Lat, Lon, Timestamp, Status");
    }

    @Test
    void locationDataGrid_dataBinding_addAndRetrieve() {
        LocationDataGrid grid = new LocationDataGrid();
        Instant now = Instant.now();
        Map<String, Object> meta = new HashMap<>();
        meta.put("vehicleName", "Truck 1");
        meta.put("realmId", "realm-a");

        LocationData data = new LocationData("v1", 37.9838, 23.7275, now, meta);
        grid.addLocationData(data);

        LocationDataGrid.LocationDataRow row = grid.getRow("v1");
        assertNotNull(row);
        assertEquals("v1", row.getVehicleId());
        assertEquals("Truck 1", row.getVehicleName());
        assertEquals("realm-a", row.getRealmId());
        assertEquals(37.9838, row.getLatitude(), 0.0001);
        assertEquals(23.7275, row.getLongitude(), 0.0001);
        assertEquals(now, row.getTimestamp());
        assertEquals("ACTIVE", row.getStatus());
    }

    @Test
    void locationDataGrid_dataBinding_updateExistingVehicle() {
        LocationDataGrid grid = new LocationDataGrid();

        grid.addLocationData(new LocationData("v1", 37.0, 23.0, Instant.parse("2024-01-01T10:00:00Z"), new HashMap<>()));
        grid.addLocationData(new LocationData("v1", 38.0, 24.0, Instant.parse("2024-01-01T11:00:00Z"), new HashMap<>()));

        assertEquals(1, grid.getRowCount(), "Update should not create new row");
        LocationDataGrid.LocationDataRow row = grid.getRow("v1");
        assertEquals(38.0, row.getLatitude(), 0.0001);
        assertEquals(24.0, row.getLongitude(), 0.0001);
    }

    @Test
    void locationDataGrid_implementsLocationDataListener() {
        LocationDataGrid grid = new LocationDataGrid();
        assertTrue(grid instanceof com.fms.consumer.integration.LocationDataListener,
                "LocationDataGrid must implement LocationDataListener for real-time updates");
    }

    @Test
    void locationDataGrid_clear_removesAllData() {
        LocationDataGrid grid = new LocationDataGrid();
        grid.addLocationData(new LocationData("v1", 37.0, 23.0, Instant.now(), new HashMap<>()));
        grid.addLocationData(new LocationData("v2", 38.0, 24.0, Instant.now(), new HashMap<>()));

        grid.clear();

        assertEquals(0, grid.getRowCount());
        assertNull(grid.getRow("v1"));
        assertNull(grid.getRow("v2"));
    }

    // === MetricsPanel Tests (Req 10.5) ===

    @Test
    void metricsPanel_initialization_showsZeroValues() {
        MetricsPanel panel = new MetricsPanel();

        assertEquals("Active Connections: 0", panel.getActiveConnectionsLabel().getText());
        assertEquals("Updates/sec: 0.0", panel.getUpdatesPerSecondLabel().getText());
        assertEquals("Active Vehicles: 0", panel.getActiveVehiclesLabel().getText());
        assertEquals("Active Realms: 0", panel.getActiveRealmsLabel().getText());

        panel.destroy();
    }

    @Test
    void metricsPanel_perClientSection_hiddenByDefault() {
        MetricsPanel panel = new MetricsPanel();

        assertFalse(panel.getPerClientSection().isVisible());
        assertFalse(panel.getPerClientHeader().isVisible());

        panel.destroy();
    }

    @Test
    void metricsPanel_startStop_lifecycle() {
        MetricsPanel panel = new MetricsPanel();
        MetricsCollector collector = mock(MetricsCollector.class);
        panel.setMetricsCollector(collector);

        assertDoesNotThrow(() -> panel.start());
        assertDoesNotThrow(() -> panel.stop());
        assertDoesNotThrow(() -> panel.destroy());
    }

    // === MultiClientConfigPanel Tests (Req 10.6) ===

    @Test
    void multiClientConfigPanel_initialization_defaultValues() {
        MultiClientConfigPanel panel = new MultiClientConfigPanel();

        assertEquals(1, panel.getClientCountField().getValue());
        assertEquals("Current clients: 1", panel.getStatusLabel().getText());
        assertTrue(panel.getConfigureButton().isEnabled());
        assertEquals(1, panel.getClientCountField().getMin());
        assertEquals(100, panel.getClientCountField().getMax());
    }

    @Test
    void multiClientConfigPanel_configureButton_callsOrchestrator() {
        MultiClientConfigPanel panel = new MultiClientConfigPanel();
        ConsumptionOrchestrator orchestrator = mock(ConsumptionOrchestrator.class);
        when(orchestrator.getClientCount()).thenReturn(1);
        panel.setOrchestrator(orchestrator);

        panel.getClientCountField().setValue(5);
        panel.getConfigureButton().click();

        verify(orchestrator).configureMultiClient(5);
    }

    @Test
    void multiClientConfigPanel_invalidValues_notSentToOrchestrator() {
        MultiClientConfigPanel panel = new MultiClientConfigPanel();
        ConsumptionOrchestrator orchestrator = mock(ConsumptionOrchestrator.class);
        when(orchestrator.getClientCount()).thenReturn(1);
        panel.setOrchestrator(orchestrator);

        // Value below minimum
        panel.getClientCountField().setValue(0);
        panel.getConfigureButton().click();
        verify(orchestrator, never()).configureMultiClient(0);

        // Value above maximum
        panel.getClientCountField().setValue(101);
        panel.getConfigureButton().click();
        verify(orchestrator, never()).configureMultiClient(101);
    }

    @Test
    void multiClientConfigPanel_refreshStatus_updatesFromOrchestrator() {
        MultiClientConfigPanel panel = new MultiClientConfigPanel();
        ConsumptionOrchestrator orchestrator = mock(ConsumptionOrchestrator.class);
        when(orchestrator.getClientCount()).thenReturn(3);
        panel.setOrchestrator(orchestrator);

        assertEquals("Current clients: 3", panel.getStatusLabel().getText());
        assertEquals(3, panel.getClientCountField().getValue());
    }

    @Test
    void multiClientConfigPanel_withoutOrchestrator_doesNotThrow() {
        MultiClientConfigPanel panel = new MultiClientConfigPanel();
        panel.getClientCountField().setValue(10);
        assertDoesNotThrow(() -> panel.getConfigureButton().click());
    }
}
