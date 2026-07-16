package com.fms.consumer.ui;

import com.fms.consumer.model.LocationData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the LocationDataGrid component.
 * Tests grid initialization, data upsert behavior, clear, getRowCount, and getRows methods.
 */
class LocationDataGridTest {

    private LocationDataGrid grid;

    @BeforeEach
    void setUp() {
        grid = new LocationDataGrid();
    }

    @Test
    void constructor_initialState_emptyGrid() {
        assertEquals(0, grid.getRowCount());
        assertTrue(grid.getRows().isEmpty());
    }

    @Test
    void constructor_gridHasCorrectColumns() {
        // Grid should have 7 columns: Vehicle ID, Vehicle Name, Realm, Latitude, Longitude, Last Update, Status
        assertEquals(7, grid.getGrid().getColumns().size());
    }

    @Test
    void addLocationData_newVehicle_addsRow() {
        LocationData data = createLocationData("v1", 37.9838, 23.7275, Instant.now());

        grid.addLocationData(data);

        assertEquals(1, grid.getRowCount());
        LocationDataGrid.LocationDataRow row = grid.getRow("v1");
        assertNotNull(row);
        assertEquals("v1", row.getVehicleId());
        assertEquals(37.9838, row.getLatitude(), 0.0001);
        assertEquals(23.7275, row.getLongitude(), 0.0001);
        assertEquals("ACTIVE", row.getStatus());
    }

    @Test
    void addLocationData_existingVehicle_updatesRow() {
        Instant first = Instant.parse("2024-01-01T10:00:00Z");
        Instant second = Instant.parse("2024-01-01T10:01:00Z");

        grid.addLocationData(createLocationData("v1", 37.9838, 23.7275, first));
        grid.addLocationData(createLocationData("v1", 38.0000, 24.0000, second));

        assertEquals(1, grid.getRowCount());
        LocationDataGrid.LocationDataRow row = grid.getRow("v1");
        assertNotNull(row);
        assertEquals(38.0000, row.getLatitude(), 0.0001);
        assertEquals(24.0000, row.getLongitude(), 0.0001);
        assertEquals(second, row.getTimestamp());
    }

    @Test
    void addLocationData_multipleVehicles_addsMultipleRows() {
        grid.addLocationData(createLocationData("v1", 37.9838, 23.7275, Instant.now()));
        grid.addLocationData(createLocationData("v2", 40.6401, 22.9444, Instant.now()));
        grid.addLocationData(createLocationData("v3", 35.3387, 25.1442, Instant.now()));

        assertEquals(3, grid.getRowCount());
    }

    @Test
    void addLocationData_withMetadata_extractsVehicleNameAndRealm() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("vehicleName", "Truck 1");
        metadata.put("realmId", "realm-a");

        LocationData data = new LocationData("v1", 37.9838, 23.7275, Instant.now(), metadata);
        grid.addLocationData(data);

        LocationDataGrid.LocationDataRow row = grid.getRow("v1");
        assertNotNull(row);
        assertEquals("Truck 1", row.getVehicleName());
        assertEquals("realm-a", row.getRealmId());
    }

    @Test
    void addLocationData_nullData_doesNothing() {
        grid.addLocationData(null);
        assertEquals(0, grid.getRowCount());
    }

    @Test
    void addLocationData_nullVehicleId_doesNothing() {
        LocationData data = new LocationData();
        data.setVehicleId(null);
        data.setLatitude(37.0);
        data.setLongitude(23.0);

        grid.addLocationData(data);
        assertEquals(0, grid.getRowCount());
    }

    @Test
    void clear_removesAllRows() {
        grid.addLocationData(createLocationData("v1", 37.0, 23.0, Instant.now()));
        grid.addLocationData(createLocationData("v2", 38.0, 24.0, Instant.now()));
        assertEquals(2, grid.getRowCount());

        grid.clear();

        assertEquals(0, grid.getRowCount());
        assertTrue(grid.getRows().isEmpty());
    }

    @Test
    void clearAll_removesAllRows() {
        grid.addLocationData(createLocationData("v1", 37.0, 23.0, Instant.now()));
        grid.clearAll();
        assertEquals(0, grid.getRowCount());
    }

    @Test
    void getRows_returnsSnapshotOfAllRows() {
        grid.addLocationData(createLocationData("v1", 37.0, 23.0, Instant.now()));
        grid.addLocationData(createLocationData("v2", 38.0, 24.0, Instant.now()));

        List<LocationDataGrid.LocationDataRow> rows = grid.getRows();

        assertEquals(2, rows.size());
    }

    @Test
    void getRowCount_returnsCorrectCount() {
        assertEquals(0, grid.getRowCount());

        grid.addLocationData(createLocationData("v1", 37.0, 23.0, Instant.now()));
        assertEquals(1, grid.getRowCount());

        grid.addLocationData(createLocationData("v2", 38.0, 24.0, Instant.now()));
        assertEquals(2, grid.getRowCount());
    }

    @Test
    void removeVehicle_removesSpecificRow() {
        grid.addLocationData(createLocationData("v1", 37.0, 23.0, Instant.now()));
        grid.addLocationData(createLocationData("v2", 38.0, 24.0, Instant.now()));

        grid.removeVehicle("v1");

        assertEquals(1, grid.getRowCount());
        assertNull(grid.getRow("v1"));
        assertNotNull(grid.getRow("v2"));
    }

    @Test
    void removeVehicle_nullVehicleId_doesNothing() {
        grid.addLocationData(createLocationData("v1", 37.0, 23.0, Instant.now()));
        grid.removeVehicle(null);
        assertEquals(1, grid.getRowCount());
    }

    @Test
    void removeVehicle_nonExistent_doesNothing() {
        grid.addLocationData(createLocationData("v1", 37.0, 23.0, Instant.now()));
        grid.removeVehicle("v99");
        assertEquals(1, grid.getRowCount());
    }

    @Test
    void getRow_existingVehicle_returnsRow() {
        grid.addLocationData(createLocationData("v1", 37.0, 23.0, Instant.now()));
        assertNotNull(grid.getRow("v1"));
    }

    @Test
    void getRow_nonExistent_returnsNull() {
        assertNull(grid.getRow("v99"));
    }

    @Test
    void locationDataRow_formattedTimestamp_withNull_returnsNA() {
        LocationDataGrid.LocationDataRow row = new LocationDataGrid.LocationDataRow();
        assertEquals("N/A", row.getFormattedTimestamp());
    }

    @Test
    void locationDataRow_formattedTimestamp_withValue_returnsFormatted() {
        LocationDataGrid.LocationDataRow row = new LocationDataGrid.LocationDataRow();
        row.setTimestamp(Instant.parse("2024-01-01T12:00:00Z"));
        String formatted = row.getFormattedTimestamp();
        assertNotNull(formatted);
        assertNotEquals("N/A", formatted);
        // The formatted string should contain the date parts
        assertTrue(formatted.contains("2024"));
    }

    @Test
    void locationDataRow_equals_sameVehicleId() {
        LocationDataGrid.LocationDataRow row1 = new LocationDataGrid.LocationDataRow();
        row1.setVehicleId("v1");

        LocationDataGrid.LocationDataRow row2 = new LocationDataGrid.LocationDataRow();
        row2.setVehicleId("v1");

        assertEquals(row1, row2);
        assertEquals(row1.hashCode(), row2.hashCode());
    }

    @Test
    void locationDataRow_notEquals_differentVehicleId() {
        LocationDataGrid.LocationDataRow row1 = new LocationDataGrid.LocationDataRow();
        row1.setVehicleId("v1");

        LocationDataGrid.LocationDataRow row2 = new LocationDataGrid.LocationDataRow();
        row2.setVehicleId("v2");

        assertNotEquals(row1, row2);
    }

    @Test
    void locationDataRow_setMetadata_nullSetsEmptyMap() {
        LocationDataGrid.LocationDataRow row = new LocationDataGrid.LocationDataRow();
        row.setMetadata(null);
        assertNotNull(row.getMetadata());
        assertTrue(row.getMetadata().isEmpty());
    }

    @Test
    void locationDataRow_fullConstructor_setsAllFields() {
        Instant now = Instant.now();
        Map<String, Object> meta = new HashMap<>();
        meta.put("key", "value");

        LocationDataGrid.LocationDataRow row = new LocationDataGrid.LocationDataRow(
                "v1", "Truck 1", "realm-a", 37.0, 23.0, now, "ACTIVE", meta);

        assertEquals("v1", row.getVehicleId());
        assertEquals("Truck 1", row.getVehicleName());
        assertEquals("realm-a", row.getRealmId());
        assertEquals(37.0, row.getLatitude(), 0.0001);
        assertEquals(23.0, row.getLongitude(), 0.0001);
        assertEquals(now, row.getTimestamp());
        assertEquals("ACTIVE", row.getStatus());
        assertEquals("value", row.getMetadata().get("key"));
    }

    @Test
    void implementsLocationDataListener() {
        assertTrue(grid instanceof com.fms.consumer.integration.LocationDataListener);
    }

    // --- Helper ---

    private LocationData createLocationData(String vehicleId, double lat, double lon, Instant timestamp) {
        return new LocationData(vehicleId, lat, lon, timestamp, new HashMap<>());
    }
}
