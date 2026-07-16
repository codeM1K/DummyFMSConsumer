package com.fms.consumer.ui;

import com.fms.consumer.integration.LocationDataListener;
import com.fms.consumer.model.LocationData;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Vaadin Grid component that displays real-time vehicle location data.
 * <p>
 * Implements {@link LocationDataListener} to receive location data updates from
 * the {@link com.fms.consumer.integration.LocationDataHandler}. Uses Vaadin Push
 * (via {@code UI.access()}) for thread-safe UI updates from background threads.
 * <p>
 * The grid upserts rows by vehicleId: if a vehicle already exists in the grid,
 * its data is updated; otherwise a new row is added.
 */
public class LocationDataGrid extends VerticalLayout implements LocationDataListener {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    /**
     * Represents a single row in the location data grid.
     * Contains all display fields for a vehicle's latest location data.
     */
    public static class LocationDataRow {
        private String vehicleId;
        private String vehicleName;
        private String realmId;
        private double latitude;
        private double longitude;
        private Instant timestamp;
        private String status;
        private Map<String, Object> metadata;

        public LocationDataRow() {
            this.metadata = new ConcurrentHashMap<>();
        }

        public LocationDataRow(String vehicleId, String vehicleName, String realmId,
                               double latitude, double longitude, Instant timestamp,
                               String status, Map<String, Object> metadata) {
            this.vehicleId = vehicleId;
            this.vehicleName = vehicleName;
            this.realmId = realmId;
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
            this.status = status;
            this.metadata = metadata != null ? metadata : new ConcurrentHashMap<>();
        }

        public String getVehicleId() {
            return vehicleId;
        }

        public void setVehicleId(String vehicleId) {
            this.vehicleId = vehicleId;
        }

        public String getVehicleName() {
            return vehicleName;
        }

        public void setVehicleName(String vehicleName) {
            this.vehicleName = vehicleName;
        }

        public String getRealmId() {
            return realmId;
        }

        public void setRealmId(String realmId) {
            this.realmId = realmId;
        }

        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? metadata : new ConcurrentHashMap<>();
        }

        /**
         * Returns a formatted timestamp string for display in the grid.
         *
         * @return formatted timestamp or "N/A" if timestamp is null
         */
        public String getFormattedTimestamp() {
            if (timestamp == null) {
                return "N/A";
            }
            return TIMESTAMP_FORMATTER.format(timestamp);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LocationDataRow that = (LocationDataRow) o;
            return Objects.equals(vehicleId, that.vehicleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(vehicleId);
        }
    }

    private final Grid<LocationDataRow> grid;
    private final ConcurrentHashMap<String, LocationDataRow> rowsByVehicleId;

    /**
     * Creates a new LocationDataGrid with configured columns and an empty data set.
     */
    public LocationDataGrid() {
        this.rowsByVehicleId = new ConcurrentHashMap<>();
        this.grid = new Grid<>(LocationDataRow.class, false);

        configureGrid();

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        add(grid);
    }

    private void configureGrid() {
        grid.addColumn(LocationDataRow::getVehicleId)
                .setHeader("Vehicle ID")
                .setFlexGrow(1)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(LocationDataRow::getVehicleName)
                .setHeader("Vehicle Name")
                .setFlexGrow(1)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(LocationDataRow::getRealmId)
                .setHeader("Realm")
                .setFlexGrow(1)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(LocationDataRow::getLatitude)
                .setHeader("Latitude")
                .setFlexGrow(1)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(LocationDataRow::getLongitude)
                .setHeader("Longitude")
                .setFlexGrow(1)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(LocationDataRow::getFormattedTimestamp)
                .setHeader("Last Update")
                .setFlexGrow(1)
                .setResizable(true)
                .setSortable(true);

        grid.addColumn(LocationDataRow::getStatus)
                .setHeader("Status")
                .setFlexGrow(1)
                .setResizable(true)
                .setSortable(true);

        grid.setSizeFull();
    }

    /**
     * Adds or updates location data for a vehicle in the grid.
     * If the vehicle already exists, its row is updated with the new data.
     * If it's a new vehicle, a new row is added.
     *
     * @param data the location data to add or update
     */
    public void addLocationData(LocationData data) {
        if (data == null || data.getVehicleId() == null) {
            return;
        }

        LocationDataRow row = rowsByVehicleId.computeIfAbsent(data.getVehicleId(), id -> {
            LocationDataRow newRow = new LocationDataRow();
            newRow.setVehicleId(id);
            return newRow;
        });

        // Update row data
        row.setLatitude(data.getLatitude());
        row.setLongitude(data.getLongitude());
        row.setTimestamp(data.getTimestamp());
        row.setStatus("ACTIVE");

        if (data.getMetadata() != null) {
            row.setMetadata(data.getMetadata());

            // Extract vehicle name from metadata if available
            Object vehicleName = data.getMetadata().get("vehicleName");
            if (vehicleName != null) {
                row.setVehicleName(vehicleName.toString());
            }

            // Extract realm ID from metadata if available
            Object realmId = data.getMetadata().get("realmId");
            if (realmId != null) {
                row.setRealmId(realmId.toString());
            }
        }

        refreshGrid();
    }

    /**
     * Clears all data from the grid.
     */
    public void clearAll() {
        rowsByVehicleId.clear();
        refreshGrid();
    }

    /**
     * Clears all data from the grid.
     * Alias for {@link #clearAll()} to match the expected interface.
     */
    public void clear() {
        clearAll();
    }

    /**
     * Returns an unmodifiable snapshot of all rows currently in the grid.
     *
     * @return a list of all LocationDataRow entries
     */
    public List<LocationDataRow> getRows() {
        return new ArrayList<>(rowsByVehicleId.values());
    }

    /**
     * Removes a specific vehicle from the grid.
     *
     * @param vehicleId the ID of the vehicle to remove
     */
    public void removeVehicle(String vehicleId) {
        if (vehicleId != null) {
            rowsByVehicleId.remove(vehicleId);
            refreshGrid();
        }
    }

    /**
     * Returns the number of vehicles currently displayed in the grid.
     *
     * @return the row count
     */
    public int getRowCount() {
        return rowsByVehicleId.size();
    }

    /**
     * Returns the row data for a specific vehicle.
     *
     * @param vehicleId the vehicle ID to look up
     * @return the row data or null if not found
     */
    public LocationDataRow getRow(String vehicleId) {
        return rowsByVehicleId.get(vehicleId);
    }

    /**
     * Returns the underlying Vaadin Grid for advanced customization.
     *
     * @return the Grid component
     */
    public Grid<LocationDataRow> getGrid() {
        return grid;
    }

    // --- LocationDataListener implementation ---

    /**
     * Receives location data from the LocationDataHandler.
     * Uses UI.access() for thread-safe UI updates from background threads.
     *
     * @param data the parsed location data
     */
    @Override
    public void onLocationDataReceived(LocationData data) {
        getUI().ifPresent(ui -> ui.access(() -> addLocationData(data)));
    }

    // --- Internal helpers ---

    private void refreshGrid() {
        grid.setItems(rowsByVehicleId.values());
    }
}
