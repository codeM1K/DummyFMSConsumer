package com.fms.consumer.ui;

import com.fms.consumer.model.Realm;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.model.VehicleStatus;
import com.fms.consumer.ui.RealmVehicleTree.TreeNode;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for hierarchical realm-vehicle display in the {@link RealmVehicleTree} component.
 *
 * <p><b>Property 32: Hierarchical Realm-Vehicle Display</b></p>
 * <p>"For any combination of realms and their associated vehicles, the UI SHALL display them
 * in a hierarchical tree structure with realms as parent nodes and vehicles as child nodes."</p>
 *
 * <p><b>Validates: Requirements 10.2</b></p>
 */
class HierarchicalDisplayPropertyTest {

    /**
     * For any list of realms, the tree SHALL have exactly two levels of hierarchy:
     * realm nodes at the root level (level 0) and vehicle nodes as children (level 1).
     *
     * <p><b>Validates: Requirements 10.2</b></p>
     */
    @Property(tries = 200)
    void treeHasTwoLevelHierarchy(
            @ForAll("realmsWithVehicles") List<Realm> realms) {

        RealmVehicleTree tree = new RealmVehicleTree();
        tree.setData(realms);

        @SuppressWarnings("unchecked")
        TreeDataProvider<TreeNode> dataProvider =
                (TreeDataProvider<TreeNode>) tree.getTreeGrid().getDataProvider();
        TreeData<TreeNode> treeData = dataProvider.getTreeData();

        // Root items are level 0 (realms)
        List<TreeNode> rootItems = treeData.getRootItems();
        for (TreeNode root : rootItems) {
            assertTrue(root.isRealm(),
                    "All root-level nodes must be realm nodes");

            // Children of root are level 1 (vehicles)
            List<TreeNode> children = treeData.getChildren(root);
            for (TreeNode child : children) {
                assertTrue(child.isVehicle(),
                        "All children of realm nodes must be vehicle nodes");

                // Level 1 nodes must have no children (two-level only)
                List<TreeNode> grandchildren = treeData.getChildren(child);
                assertTrue(grandchildren.isEmpty(),
                        "Vehicle nodes must not have children (only two levels allowed)");
            }
        }
    }

    /**
     * For any realm in the tree, the hierarchy SHALL correctly map: the realm
     * is the parent and only its associated vehicles are its children.
     *
     * <p><b>Validates: Requirements 10.2</b></p>
     */
    @Property(tries = 200)
    void realmParentChildRelationshipIsCorrect(
            @ForAll("realmsWithVehicles") List<Realm> realms) {

        RealmVehicleTree tree = new RealmVehicleTree();
        tree.setData(realms);

        @SuppressWarnings("unchecked")
        TreeDataProvider<TreeNode> dataProvider =
                (TreeDataProvider<TreeNode>) tree.getTreeGrid().getDataProvider();
        TreeData<TreeNode> treeData = dataProvider.getTreeData();

        for (Realm realm : realms) {
            TreeNode realmNode = new TreeNode(realm);
            List<TreeNode> children = treeData.getChildren(realmNode);

            int expectedChildCount = realm.getVehicles() != null ? realm.getVehicles().size() : 0;
            assertEquals(expectedChildCount, children.size(),
                    "Realm '" + realm.getName() + "' must have exactly " + expectedChildCount + " child nodes");

            // Each child must be a vehicle belonging to this realm
            if (realm.getVehicles() != null) {
                Set<String> expectedVehicleIds = new HashSet<>();
                for (Vehicle v : realm.getVehicles()) {
                    expectedVehicleIds.add(v.getId());
                }

                for (TreeNode child : children) {
                    assertTrue(expectedVehicleIds.contains(child.getId()),
                            "Child '" + child.getId() + "' must belong to realm '" + realm.getId() + "'");
                }
            }
        }
    }

    /**
     * For any non-empty realm set, the tree SHALL preserve the realm order
     * (realms appear in the tree in the same order they were provided).
     *
     * <p><b>Validates: Requirements 10.2</b></p>
     */
    @Property(tries = 200)
    void treePreservesRealmOrder(
            @ForAll("realmsWithVehicles") List<Realm> realms) {

        RealmVehicleTree tree = new RealmVehicleTree();
        tree.setData(realms);

        @SuppressWarnings("unchecked")
        TreeDataProvider<TreeNode> dataProvider =
                (TreeDataProvider<TreeNode>) tree.getTreeGrid().getDataProvider();
        TreeData<TreeNode> treeData = dataProvider.getTreeData();

        List<TreeNode> rootItems = treeData.getRootItems();
        assertEquals(realms.size(), rootItems.size());

        for (int i = 0; i < realms.size(); i++) {
            assertEquals(realms.get(i).getId(), rootItems.get(i).getId(),
                    "Realm at position " + i + " must match the input order");
        }
    }

    // --- Providers ---

    @Provide
    Arbitrary<List<Realm>> realmsWithVehicles() {
        return Arbitraries.integers().between(1, 6).flatMap(realmCount -> {
            List<Arbitrary<Realm>> realmArbitraries = new ArrayList<>();
            for (int i = 0; i < realmCount; i++) {
                int realmIdx = i;
                realmArbitraries.add(
                        Arbitraries.integers().between(0, 6).map(vehicleCount -> {
                            String realmId = "realm-" + realmIdx;
                            String realmName = "Realm " + realmIdx;
                            List<Vehicle> vehicles = new ArrayList<>();
                            for (int v = 0; v < vehicleCount; v++) {
                                String vehicleId = realmId + "-vehicle-" + v;
                                vehicles.add(new Vehicle(vehicleId, "Vehicle " + v,
                                        realmId, VehicleStatus.ACTIVE));
                            }
                            return new Realm(realmId, realmName, vehicles);
                        })
                );
            }
            return Combinators.combine(realmArbitraries).as(realms -> realms);
        });
    }
}
