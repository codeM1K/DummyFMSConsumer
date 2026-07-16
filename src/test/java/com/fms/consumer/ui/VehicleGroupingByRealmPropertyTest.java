package com.fms.consumer.ui;

import com.fms.consumer.model.Realm;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.model.VehicleStatus;
import com.fms.consumer.ui.RealmVehicleTree.TreeNode;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for vehicle grouping by realm in the {@link RealmVehicleTree} component.
 *
 * <p><b>Property 7: Vehicle Grouping by Realm</b></p>
 * <p>"For any combination of realms and vehicles, the UI SHALL display vehicles grouped
 * by their associated realm in a hierarchical structure."</p>
 *
 * <p><b>Validates: Requirements 3.2</b></p>
 */
class VehicleGroupingByRealmPropertyTest {

    /**
     * For any list of realms with vehicles, after calling setData(), each vehicle
     * SHALL appear as a child node of its associated realm node in the tree hierarchy.
     *
     * <p><b>Validates: Requirements 3.2</b></p>
     */
    @Property(tries = 200)
    void everyVehicleAppearsAsChildOfItsAssociatedRealm(
            @ForAll("realmsWithVehicles") List<Realm> realms) {

        RealmVehicleTree tree = new RealmVehicleTree();
        tree.setData(realms);

        @SuppressWarnings("unchecked")
        TreeDataProvider<TreeNode> dataProvider =
                (TreeDataProvider<TreeNode>) tree.getTreeGrid().getDataProvider();
        TreeData<TreeNode> treeData = dataProvider.getTreeData();

        // For each realm, verify its vehicles appear as children
        for (Realm realm : realms) {
            TreeNode realmNode = new TreeNode(realm);

            // The realm node must be a root item
            List<TreeNode> rootItems = treeData.getRootItems();
            assertTrue(rootItems.contains(realmNode),
                    "Realm '" + realm.getName() + "' (id=" + realm.getId() +
                            ") must appear as a root node in the tree");

            // Get the children of this realm node
            List<TreeNode> children = treeData.getChildren(realmNode);

            // Each vehicle in the realm should be a child of the realm node
            if (realm.getVehicles() != null) {
                for (Vehicle vehicle : realm.getVehicles()) {
                    TreeNode vehicleNode = new TreeNode(vehicle);
                    assertTrue(children.contains(vehicleNode),
                            "Vehicle '" + vehicle.getName() + "' (id=" + vehicle.getId() +
                                    ") must be a child of realm '" + realm.getName() + "'");
                }
            }
        }
    }

    /**
     * For any list of realms, the total number of vehicle child nodes in the tree
     * SHALL equal the total number of vehicles across all realms.
     *
     * <p><b>Validates: Requirements 3.2</b></p>
     */
    @Property(tries = 200)
    void totalVehicleNodesEqualsVehicleCount(
            @ForAll("realmsWithVehicles") List<Realm> realms) {

        RealmVehicleTree tree = new RealmVehicleTree();
        tree.setData(realms);

        @SuppressWarnings("unchecked")
        TreeDataProvider<TreeNode> dataProvider =
                (TreeDataProvider<TreeNode>) tree.getTreeGrid().getDataProvider();
        TreeData<TreeNode> treeData = dataProvider.getTreeData();

        int expectedVehicleCount = realms.stream()
                .mapToInt(r -> r.getVehicles() != null ? r.getVehicles().size() : 0)
                .sum();

        // Count all vehicle child nodes across all realm parents
        int actualVehicleNodeCount = 0;
        for (TreeNode rootItem : treeData.getRootItems()) {
            actualVehicleNodeCount += treeData.getChildren(rootItem).size();
        }

        assertEquals(expectedVehicleCount, actualVehicleNodeCount,
                "Total vehicle nodes in tree must equal total vehicles across all realms");
    }

    /**
     * For any list of realms with vehicles, no vehicle SHALL appear under a realm
     * it does not belong to (no cross-realm misplacement).
     *
     * <p><b>Validates: Requirements 3.2</b></p>
     */
    @Property(tries = 200)
    void noVehicleAppearsUnderWrongRealm(
            @ForAll("realmsWithVehicles") List<Realm> realms) {

        RealmVehicleTree tree = new RealmVehicleTree();
        tree.setData(realms);

        @SuppressWarnings("unchecked")
        TreeDataProvider<TreeNode> dataProvider =
                (TreeDataProvider<TreeNode>) tree.getTreeGrid().getDataProvider();
        TreeData<TreeNode> treeData = dataProvider.getTreeData();

        // Build a map: realmId -> set of vehicle IDs that belong to it
        Map<String, Set<String>> realmToVehicleIds = new HashMap<>();
        for (Realm realm : realms) {
            Set<String> vehicleIds = new HashSet<>();
            if (realm.getVehicles() != null) {
                for (Vehicle v : realm.getVehicles()) {
                    vehicleIds.add(v.getId());
                }
            }
            realmToVehicleIds.put(realm.getId(), vehicleIds);
        }

        // For each realm node, verify all children belong to that realm
        for (TreeNode rootItem : treeData.getRootItems()) {
            assertTrue(rootItem.isRealm(), "Root items must be realm nodes");
            String realmId = rootItem.getId();
            Set<String> expectedVehicleIds = realmToVehicleIds.get(realmId);

            for (TreeNode child : treeData.getChildren(rootItem)) {
                assertTrue(child.isVehicle(),
                        "Children of realm nodes must be vehicle nodes");
                assertTrue(expectedVehicleIds.contains(child.getId()),
                        "Vehicle '" + child.getId() + "' should not appear under realm '" +
                                realmId + "' — it doesn't belong there");
            }
        }
    }

    // --- Providers ---

    /**
     * Generates 1-5 realms, each with 0-8 vehicles with unique IDs.
     */
    @Provide
    Arbitrary<List<Realm>> realmsWithVehicles() {
        return Arbitraries.integers().between(1, 5).flatMap(realmCount -> {
            List<Arbitrary<Realm>> realmArbitraries = new ArrayList<>();
            for (int i = 0; i < realmCount; i++) {
                int realmIdx = i;
                realmArbitraries.add(
                        Arbitraries.integers().between(0, 8).map(vehicleCount -> {
                            String realmId = "realm-" + realmIdx;
                            String realmName = "Realm " + realmIdx;
                            List<Vehicle> vehicles = new ArrayList<>();
                            for (int v = 0; v < vehicleCount; v++) {
                                String vehicleId = realmId + "-veh-" + v;
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
