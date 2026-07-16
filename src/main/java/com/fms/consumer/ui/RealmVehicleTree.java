package com.fms.consumer.ui;

import com.fms.consumer.model.Realm;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.service.DiscoveryListener;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A hierarchical tree component that displays realms as parent nodes
 * and vehicles as child nodes using a Vaadin TreeGrid.
 * <p>
 * Supports multi-selection with cascading: selecting a realm
 * automatically selects all its vehicles.
 * <p>
 * Implements {@link DiscoveryListener} to receive real-time
 * realm/vehicle updates from the DiscoveryService.
 */
public class RealmVehicleTree extends VerticalLayout implements DiscoveryListener {

    /**
     * Wrapper that represents a node in the tree.
     * Can be either a Realm node or a Vehicle node.
     */
    public static class TreeNode {
        private final String id;
        private final String name;
        private final String type;
        private final String status;
        private final Realm realm;
        private final Vehicle vehicle;

        /** Creates a realm node. */
        public TreeNode(Realm realm) {
            this.id = realm.getId();
            this.name = realm.getName();
            this.type = "Realm";
            this.status = "";
            this.realm = realm;
            this.vehicle = null;
        }

        /** Creates a vehicle node. */
        public TreeNode(Vehicle vehicle) {
            this.id = vehicle.getId();
            this.name = vehicle.getName();
            this.type = "Vehicle";
            this.status = vehicle.getStatus() != null ? vehicle.getStatus().name() : "";
            this.realm = null;
            this.vehicle = vehicle;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getStatus() {
            return status;
        }

        public boolean isRealm() {
            return realm != null;
        }

        public boolean isVehicle() {
            return vehicle != null;
        }

        public Realm getRealm() {
            return realm;
        }

        public Vehicle getVehicle() {
            return vehicle;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TreeNode treeNode = (TreeNode) o;
            return Objects.equals(id, treeNode.id) && Objects.equals(type, treeNode.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, type);
        }
    }

    private final TreeGrid<TreeNode> treeGrid;
    private final TreeData<TreeNode> treeData;
    private final TreeDataProvider<TreeNode> dataProvider;

    private final Set<Vehicle> selectedVehicles = new LinkedHashSet<>();
    private final Set<TreeNode> selectedNodes = new LinkedHashSet<>();
    private final List<SelectionChangeListener> selectionChangeListeners = new CopyOnWriteArrayList<>();

    // Keeps track of realms for cascading operations
    private final Map<String, TreeNode> realmNodes = new LinkedHashMap<>();
    private final Map<String, List<TreeNode>> realmVehicleNodes = new LinkedHashMap<>();

    // Once true, realm updates are blocked (keeps tree stable after initial load)
    private volatile boolean realmsLoaded = false;
    // Tracks which realms have already had their vehicles populated
    private final Set<String> vehiclesLoadedForRealms = ConcurrentHashMap.newKeySet();

    /**
     * Listener interface for vehicle selection changes.
     */
    @FunctionalInterface
    public interface SelectionChangeListener {
        void onSelectionChanged(Set<Vehicle> selectedVehicles);
    }

    /**
     * Creates a new RealmVehicleTree with an empty tree.
     */
    public RealmVehicleTree() {
        treeData = new TreeData<>();
        dataProvider = new TreeDataProvider<>(treeData);
        treeGrid = new TreeGrid<>();

        configureTreeGrid();
        configureSelection();

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        add(treeGrid);
    }

    private void configureTreeGrid() {
        treeGrid.setDataProvider(dataProvider);
        treeGrid.setSelectionMode(Grid.SelectionMode.MULTI);

        // Hierarchical Name column
        treeGrid.addHierarchyColumn(TreeNode::getName)
                .setHeader("Name")
                .setFlexGrow(2)
                .setResizable(true);

        // Type column (Realm/Vehicle)
        treeGrid.addColumn(TreeNode::getType)
                .setHeader("Type")
                .setFlexGrow(1)
                .setResizable(true);

        // Status column
        treeGrid.addColumn(TreeNode::getStatus)
                .setHeader("Status")
                .setFlexGrow(1)
                .setResizable(true);

        treeGrid.setSizeFull();
    }

    private void configureSelection() {
        treeGrid.asMultiSelect().addValueChangeListener(event -> {
            Set<TreeNode> newSelection = event.getValue();
            Set<TreeNode> oldSelection = event.getOldValue();

            // Find newly selected nodes
            Set<TreeNode> added = new LinkedHashSet<>(newSelection);
            added.removeAll(oldSelection);

            // Find newly deselected nodes
            Set<TreeNode> removed = new LinkedHashSet<>(oldSelection);
            removed.removeAll(newSelection);

            // Handle cascading: if a realm was just selected, select all its vehicles
            Set<TreeNode> toAdd = new LinkedHashSet<>();
            for (TreeNode node : added) {
                if (node.isRealm()) {
                    List<TreeNode> vehicleNodes = realmVehicleNodes.get(node.getId());
                    if (vehicleNodes != null) {
                        toAdd.addAll(vehicleNodes);
                    }
                }
            }

            // Handle cascading: if a realm was deselected, deselect all its vehicles
            Set<TreeNode> toRemove = new LinkedHashSet<>();
            for (TreeNode node : removed) {
                if (node.isRealm()) {
                    List<TreeNode> vehicleNodes = realmVehicleNodes.get(node.getId());
                    if (vehicleNodes != null) {
                        toRemove.addAll(vehicleNodes);
                    }
                }
            }

            // Apply cascading changes
            if (!toAdd.isEmpty() || !toRemove.isEmpty()) {
                Set<TreeNode> updatedSelection = new LinkedHashSet<>(newSelection);
                updatedSelection.addAll(toAdd);
                updatedSelection.removeAll(toRemove);
                treeGrid.asMultiSelect().setValue(updatedSelection);
                return; // The setValue will trigger this listener again with the final state
            }

            // Update internal state from the final selection
            selectedNodes.clear();
            selectedNodes.addAll(newSelection);

            selectedVehicles.clear();
            for (TreeNode node : newSelection) {
                if (node.isVehicle()) {
                    selectedVehicles.add(node.getVehicle());
                }
            }

            notifySelectionListeners();
        });
    }

    /**
     * Populates the tree with the given list of realms.
     * Each realm is displayed as a parent node with its vehicles as children.
     *
     * @param realms the list of realms to display
     */
    public void setData(List<Realm> realms) {
        treeData.clear();
        realmNodes.clear();
        realmVehicleNodes.clear();
        selectedNodes.clear();
        selectedVehicles.clear();

        if (realms != null) {
            for (Realm realm : realms) {
                TreeNode realmNode = new TreeNode(realm);
                treeData.addItem(null, realmNode);
                realmNodes.put(realm.getId(), realmNode);

                List<TreeNode> vehicleNodeList = new ArrayList<>();
                if (realm.getVehicles() != null) {
                    for (Vehicle vehicle : realm.getVehicles()) {
                        TreeNode vehicleNode = new TreeNode(vehicle);
                        treeData.addItem(realmNode, vehicleNode);
                        vehicleNodeList.add(vehicleNode);
                    }
                }
                realmVehicleNodes.put(realm.getId(), vehicleNodeList);
            }
        }

        dataProvider.refreshAll();
        realmsLoaded = true;
    }

    /**
     * Returns the currently selected vehicles.
     *
     * @return an unmodifiable set of selected vehicles
     */
    public Set<Vehicle> getSelectedVehicles() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(selectedVehicles));
    }

    /**
     * Programmatically selects a vehicle by its ID.
     *
     * @param vehicleId the ID of the vehicle to select
     */
    public void selectVehicle(String vehicleId) {
        for (List<TreeNode> vehicleNodes : realmVehicleNodes.values()) {
            for (TreeNode node : vehicleNodes) {
                if (node.getId().equals(vehicleId)) {
                    Set<TreeNode> current = new LinkedHashSet<>(treeGrid.asMultiSelect().getValue());
                    current.add(node);
                    treeGrid.asMultiSelect().setValue(current);
                    return;
                }
            }
        }
    }

    /**
     * Programmatically selects all vehicles in a realm.
     *
     * @param realmId the ID of the realm whose vehicles to select
     */
    public void selectRealm(String realmId) {
        TreeNode realmNode = realmNodes.get(realmId);
        if (realmNode != null) {
            Set<TreeNode> current = new LinkedHashSet<>(treeGrid.asMultiSelect().getValue());
            current.add(realmNode);
            treeGrid.asMultiSelect().setValue(current);
        }
    }

    /**
     * Clears all selections.
     */
    public void clearSelection() {
        treeGrid.asMultiSelect().clear();
    }

    /**
     * Adds a listener to be notified of selection changes.
     *
     * @param listener the listener to add
     */
    public void addSelectionChangeListener(SelectionChangeListener listener) {
        if (listener != null) {
            selectionChangeListeners.add(listener);
        }
    }

    /**
     * Removes a selection change listener.
     *
     * @param listener the listener to remove
     */
    public void removeSelectionChangeListener(SelectionChangeListener listener) {
        selectionChangeListeners.remove(listener);
    }

    /**
     * Returns the underlying TreeGrid for advanced customization.
     *
     * @return the TreeGrid component
     */
    public TreeGrid<TreeNode> getTreeGrid() {
        return treeGrid;
    }

    // --- DiscoveryListener implementation ---

    @Override
    public void onRealmsUpdated(List<Realm> realms) {
        if (realmsLoaded) {
            return; // Realms already loaded, ignore subsequent updates
        }
        getUI().ifPresent(ui -> ui.access(() -> setData(realms)));
    }

    @Override
    public void onVehiclesUpdated(String realmId, List<Vehicle> vehicles) {
        // Allow vehicle updates only once per realm (for initial population)
        if (vehiclesLoadedForRealms.contains(realmId)) {
            return; // Already populated vehicles for this realm
        }

        getUI().ifPresent(ui -> ui.access(() -> {
            TreeNode realmNode = realmNodes.get(realmId);
            if (realmNode == null) {
                return;
            }

            // Only add if we don't have vehicles yet for this realm
            List<TreeNode> existingVehicleNodes = realmVehicleNodes.get(realmId);
            if (existingVehicleNodes != null && !existingVehicleNodes.isEmpty()) {
                vehiclesLoadedForRealms.add(realmId);
                return;
            }

            // Add vehicle nodes
            List<TreeNode> newVehicleNodes = new ArrayList<>();
            if (vehicles != null && !vehicles.isEmpty()) {
                for (Vehicle vehicle : vehicles) {
                    TreeNode vehicleNode = new TreeNode(vehicle);
                    treeData.addItem(realmNode, vehicleNode);
                    newVehicleNodes.add(vehicleNode);
                }
                realmVehicleNodes.put(realmId, newVehicleNodes);
                dataProvider.refreshAll();
            }

            vehiclesLoadedForRealms.add(realmId);
        }));
    }

    @Override
    public void onDiscoveryError(Throwable error) {
        // Error handling can be extended later (e.g., showing a notification)
    }

    private void notifySelectionListeners() {
        Set<Vehicle> snapshot = getSelectedVehicles();
        for (SelectionChangeListener listener : selectionChangeListeners) {
            listener.onSelectionChanged(snapshot);
        }
    }
}
