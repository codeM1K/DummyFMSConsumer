package com.fms.consumer.ui;

import com.fms.consumer.model.Realm;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.model.VehicleStatus;
import com.fms.consumer.ui.RealmVehicleTree.TreeNode;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import net.jqwik.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for realm display completeness in the {@link RealmVehicleTree} component.
 *
 * <p><b>Property 4: Realm Display Completeness</b></p>
 * <p>"For any retrieved realm list, all realms SHALL appear in the user interface display."</p>
 *
 * <p><b>Validates: Requirements 2.2</b></p>
 */
class RealmDisplayCompletenessPropertyTest {

    /**
     * For any list of realms passed to setData(), every realm SHALL appear as a
     * root node in the tree's data provider.
     *
     * <p><b>Validates: Requirements 2.2</b></p>
     */
    @Property(tries = 200)
    void allRealmsAppearAsRootNodesInTree(
            @ForAll("realmLists") List<Realm> realms) {

        RealmVehicleTree tree = new RealmVehicleTree();
        tree.setData(realms);

        @SuppressWarnings("unchecked")
        TreeDataProvider<TreeNode> dataProvider =
                (TreeDataProvider<TreeNode>) tree.getTreeGrid().getDataProvider();
        TreeData<TreeNode> treeData = dataProvider.getTreeData();

        List<TreeNode> rootItems = treeData.getRootItems();

        // Every realm in the input list must have a corresponding root node
        for (Realm realm : realms) {
            TreeNode expectedNode = new TreeNode(realm);
            assertTrue(rootItems.contains(expectedNode),
                    "Realm '" + realm.getName() + "' (id=" + realm.getId() +
                            ") must appear as a root node in the tree display");
        }
    }

    /**
     * For any list of realms passed to setData(), the number of root nodes in the tree
     * SHALL equal the number of realms in the input list.
     *
     * <p><b>Validates: Requirements 2.2</b></p>
     */
    @Property(tries = 200)
    void rootNodeCountEqualsRealmCount(
            @ForAll("realmLists") List<Realm> realms) {

        RealmVehicleTree tree = new RealmVehicleTree();
        tree.setData(realms);

        @SuppressWarnings("unchecked")
        TreeDataProvider<TreeNode> dataProvider =
                (TreeDataProvider<TreeNode>) tree.getTreeGrid().getDataProvider();
        TreeData<TreeNode> treeData = dataProvider.getTreeData();

        List<TreeNode> rootItems = treeData.getRootItems();

        assertEquals(realms.size(), rootItems.size(),
                "The number of root nodes in the tree must equal the number of realms provided");
    }

    /**
     * For any list of realms passed to setData(), every root node in the tree
     * SHALL be a realm-type node (not a vehicle node).
     *
     * <p><b>Validates: Requirements 2.2</b></p>
     */
    @Property(tries = 200)
    void allRootNodesAreRealmNodes(
            @ForAll("realmLists") List<Realm> realms) {

        RealmVehicleTree tree = new RealmVehicleTree();
        tree.setData(realms);

        @SuppressWarnings("unchecked")
        TreeDataProvider<TreeNode> dataProvider =
                (TreeDataProvider<TreeNode>) tree.getTreeGrid().getDataProvider();
        TreeData<TreeNode> treeData = dataProvider.getTreeData();

        for (TreeNode rootItem : treeData.getRootItems()) {
            assertTrue(rootItem.isRealm(),
                    "All root nodes must be realm nodes, but found non-realm node: " +
                            rootItem.getName() + " (type=" + rootItem.getType() + ")");
        }
    }

    // --- Providers ---

    /**
     * Generates lists of 0-10 realms, each with 0-5 vehicles, all with unique IDs.
     */
    @Provide
    Arbitrary<List<Realm>> realmLists() {
        return Arbitraries.integers().between(0, 10).flatMap(realmCount -> {
            if (realmCount == 0) {
                return Arbitraries.just(Collections.emptyList());
            }

            List<Arbitrary<Realm>> realmArbitraries = new ArrayList<>();
            for (int i = 0; i < realmCount; i++) {
                int realmIdx = i;
                realmArbitraries.add(
                        Arbitraries.integers().between(0, 5).map(vehicleCount -> {
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
