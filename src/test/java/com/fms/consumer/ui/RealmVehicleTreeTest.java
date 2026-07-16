package com.fms.consumer.ui;

import com.fms.consumer.model.Realm;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.model.VehicleStatus;
import com.fms.consumer.ui.RealmVehicleTree.TreeNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RealmVehicleTree component.
 * Tests data population, TreeNode model, and selection logic
 * without requiring a running Vaadin server.
 */
class RealmVehicleTreeTest {

    private List<Realm> sampleRealms;

    @BeforeEach
    void setUp() {
        Vehicle v1 = new Vehicle("v1", "Truck 1", "realm1", VehicleStatus.ACTIVE);
        Vehicle v2 = new Vehicle("v2", "Truck 2", "realm1", VehicleStatus.INACTIVE);
        Vehicle v3 = new Vehicle("v3", "Van 1", "realm2", VehicleStatus.ACTIVE);

        Realm realm1 = new Realm("realm1", "Athens Fleet", List.of(v1, v2));
        Realm realm2 = new Realm("realm2", "Thessaloniki Fleet", List.of(v3));

        sampleRealms = List.of(realm1, realm2);
    }

    @Test
    void treeNodeFromRealm_hasCorrectProperties() {
        Realm realm = new Realm("r1", "Test Realm", List.of());
        TreeNode node = new TreeNode(realm);

        assertEquals("r1", node.getId());
        assertEquals("Test Realm", node.getName());
        assertEquals("Realm", node.getType());
        assertEquals("", node.getStatus());
        assertTrue(node.isRealm());
        assertFalse(node.isVehicle());
        assertSame(realm, node.getRealm());
        assertNull(node.getVehicle());
    }

    @Test
    void treeNodeFromVehicle_hasCorrectProperties() {
        Vehicle vehicle = new Vehicle("v1", "Truck 1", "r1", VehicleStatus.ACTIVE);
        TreeNode node = new TreeNode(vehicle);

        assertEquals("v1", node.getId());
        assertEquals("Truck 1", node.getName());
        assertEquals("Vehicle", node.getType());
        assertEquals("ACTIVE", node.getStatus());
        assertFalse(node.isRealm());
        assertTrue(node.isVehicle());
        assertNull(node.getRealm());
        assertSame(vehicle, node.getVehicle());
    }

    @Test
    void treeNodeFromVehicle_nullStatus_showsEmpty() {
        Vehicle vehicle = new Vehicle("v1", "Truck 1", "r1", null);
        TreeNode node = new TreeNode(vehicle);

        assertEquals("", node.getStatus());
    }

    @Test
    void treeNodeEquality_sameIdAndType_areEqual() {
        Realm realm = new Realm("r1", "A", List.of());
        TreeNode node1 = new TreeNode(realm);

        Realm realm2 = new Realm("r1", "B", List.of());
        TreeNode node2 = new TreeNode(realm2);

        assertEquals(node1, node2);
        assertEquals(node1.hashCode(), node2.hashCode());
    }

    @Test
    void treeNodeEquality_differentId_areNotEqual() {
        Realm realm1 = new Realm("r1", "A", List.of());
        Realm realm2 = new Realm("r2", "A", List.of());

        TreeNode node1 = new TreeNode(realm1);
        TreeNode node2 = new TreeNode(realm2);

        assertNotEquals(node1, node2);
    }

    @Test
    void treeNodeEquality_sameIdDifferentType_areNotEqual() {
        Realm realm = new Realm("id1", "Name", List.of());
        Vehicle vehicle = new Vehicle("id1", "Name", "r1", VehicleStatus.ACTIVE);

        TreeNode realmNode = new TreeNode(realm);
        TreeNode vehicleNode = new TreeNode(vehicle);

        assertNotEquals(realmNode, vehicleNode);
    }

    @Test
    void setData_withNullList_doesNotThrow() {
        RealmVehicleTree tree = new RealmVehicleTree();
        assertDoesNotThrow(() -> tree.setData(null));
        assertTrue(tree.getSelectedVehicles().isEmpty());
    }

    @Test
    void setData_withEmptyList_doesNotThrow() {
        RealmVehicleTree tree = new RealmVehicleTree();
        assertDoesNotThrow(() -> tree.setData(List.of()));
        assertTrue(tree.getSelectedVehicles().isEmpty());
    }

    @Test
    void setData_populatesTree() {
        RealmVehicleTree tree = new RealmVehicleTree();
        tree.setData(sampleRealms);

        // After setData, no vehicles should be selected initially
        assertTrue(tree.getSelectedVehicles().isEmpty());
    }

    @Test
    void setData_withRealmHavingNullVehicles_doesNotThrow() {
        Realm realm = new Realm("r1", "Empty Realm", null);
        RealmVehicleTree tree = new RealmVehicleTree();
        assertDoesNotThrow(() -> tree.setData(List.of(realm)));
    }

    @Test
    void getSelectedVehicles_returnsUnmodifiableSet() {
        RealmVehicleTree tree = new RealmVehicleTree();
        tree.setData(sampleRealms);

        Set<Vehicle> selected = tree.getSelectedVehicles();
        assertThrows(UnsupportedOperationException.class, () ->
                selected.add(new Vehicle("x", "x", "x", VehicleStatus.ACTIVE)));
    }

    @Test
    void getTreeGrid_returnsNonNull() {
        RealmVehicleTree tree = new RealmVehicleTree();
        assertNotNull(tree.getTreeGrid());
    }

    @Test
    void addSelectionChangeListener_nullListener_doesNotThrow() {
        RealmVehicleTree tree = new RealmVehicleTree();
        assertDoesNotThrow(() -> tree.addSelectionChangeListener(null));
    }

    @Test
    void constructor_createsTreeGridWithThreeColumns() {
        RealmVehicleTree tree = new RealmVehicleTree();
        // TreeGrid should have 3 columns: Name, Type, Status
        // Plus the selection column from multi-select mode
        assertTrue(tree.getTreeGrid().getColumns().size() >= 3);
    }

    @Test
    void implementsDiscoveryListener() {
        RealmVehicleTree tree = new RealmVehicleTree();
        assertTrue(tree instanceof com.fms.consumer.service.DiscoveryListener);
    }
}
