package com.fms.consumer.ui;

import com.fms.consumer.model.LocationData;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for real-time UI updates in the {@link LocationDataGrid} component.
 *
 * <p><b>Property 33: Real-Time UI Updates</b></p>
 * <p>"For any received Location_Data update, the UI SHALL reflect the update without
 * requiring a manual page refresh."</p>
 *
 * <p><b>Validates: Requirements 10.5</b></p>
 */
class RealTimeUIUpdatesPropertyTest {

    /**
     * For any location data added to the grid, the data SHALL be immediately
     * reflected in the grid's row data (verifying synchronous update path).
     *
     * <p><b>Validates: Requirements 10.5</b></p>
     */
    @Property(tries = 200)
    void locationDataImmediatelyReflectedInGrid(
            @ForAll("locationDataEntries") LocationData data) {

        LocationDataGrid grid = new LocationDataGrid();

        // Before adding - should not exist
        assertNull(grid.getRow(data.getVehicleId()),
                "Vehicle should not exist in grid before data is added");

        // Add data
        grid.addLocationData(data);

        // After adding - should immediately be queryable
        LocationDataGrid.LocationDataRow row = grid.getRow(data.getVehicleId());
        assertNotNull(row, "Vehicle must appear in grid immediately after addLocationData");
        assertEquals(data.getLatitude(), row.getLatitude(), 0.0001,
                "Latitude must be updated immediately");
        assertEquals(data.getLongitude(), row.getLongitude(), 0.0001,
                "Longitude must be updated immediately");
        assertEquals(data.getTimestamp(), row.getTimestamp(),
                "Timestamp must be updated immediately");
    }

    /**
     * For any sequence of location data updates for multiple vehicles, the grid SHALL
     * reflect ALL updates immediately (each vehicle row shows its latest data).
     *
     * <p><b>Validates: Requirements 10.5</b></p>
     */
    @Property(tries = 200)
    void multipleVehicleUpdatesAllReflectedImmediately(
            @ForAll("multiVehicleUpdates") List<LocationData> updates) {

        LocationDataGrid grid = new LocationDataGrid();

        // Track latest data per vehicle
        Map<String, LocationData> latestPerVehicle = new LinkedHashMap<>();
        for (LocationData data : updates) {
            grid.addLocationData(data);
            latestPerVehicle.put(data.getVehicleId(), data);
        }

        // After all updates, verify each vehicle shows its latest data
        for (Map.Entry<String, LocationData> entry : latestPerVehicle.entrySet()) {
            LocationDataGrid.LocationDataRow row = grid.getRow(entry.getKey());
            assertNotNull(row, "Vehicle '" + entry.getKey() + "' must exist in grid");
            assertEquals(entry.getValue().getLatitude(), row.getLatitude(), 0.0001,
                    "Vehicle '" + entry.getKey() + "' latitude must reflect latest update");
            assertEquals(entry.getValue().getLongitude(), row.getLongitude(), 0.0001,
                    "Vehicle '" + entry.getKey() + "' longitude must reflect latest update");
        }
    }

    /**
     * For any update to an existing vehicle's data, the grid row count SHALL remain
     * unchanged (update-in-place, no page refresh or re-creation needed).
     *
     * <p><b>Validates: Requirements 10.5</b></p>
     */
    @Property(tries = 200)
    void updateDoesNotChangeRowCount(
            @ForAll("latitudes") double lat1,
            @ForAll("longitudes") double lon1,
            @ForAll("latitudes") double lat2,
            @ForAll("longitudes") double lon2) {

        LocationDataGrid grid = new LocationDataGrid();
        String vehicleId = "update-test";

        grid.addLocationData(new LocationData(vehicleId, lat1, lon1, Instant.now(), new HashMap<>()));
        assertEquals(1, grid.getRowCount(), "Should have 1 row after first add");

        grid.addLocationData(new LocationData(vehicleId, lat2, lon2, Instant.now(), new HashMap<>()));
        assertEquals(1, grid.getRowCount(), "Should still have 1 row after update (in-place update)");

        // Verify data was actually updated
        LocationDataGrid.LocationDataRow row = grid.getRow(vehicleId);
        assertEquals(lat2, row.getLatitude(), 0.0001);
        assertEquals(lon2, row.getLongitude(), 0.0001);
    }

    // --- Providers ---

    @Provide
    Arbitrary<LocationData> locationDataEntries() {
        return Arbitraries.integers().between(0, 99).flatMap(idx ->
                Arbitraries.doubles().between(-90.0, 90.0).flatMap(lat ->
                        Arbitraries.doubles().between(-180.0, 180.0).flatMap(lon ->
                                Arbitraries.longs().between(1000000000L, 2000000000L).map(ts ->
                                        new LocationData("vehicle-" + idx, lat, lon,
                                                Instant.ofEpochSecond(ts), new HashMap<>())
                                )
                        )
                )
        );
    }

    @Provide
    Arbitrary<List<LocationData>> multiVehicleUpdates() {
        return Arbitraries.integers().between(2, 8).flatMap(count -> {
            List<Arbitrary<LocationData>> dataArbitraries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int idx = i % 4; // reuse some vehicle IDs to test updates
                dataArbitraries.add(
                        Arbitraries.doubles().between(-90.0, 90.0).flatMap(lat ->
                                Arbitraries.doubles().between(-180.0, 180.0).map(lon ->
                                        new LocationData("vehicle-" + idx, lat, lon,
                                                Instant.now(), new HashMap<>())
                                )
                        )
                );
            }
            return Combinators.combine(dataArbitraries).as(list -> list);
        });
    }

    @Provide
    Arbitrary<Double> latitudes() {
        return Arbitraries.doubles().between(-90.0, 90.0);
    }

    @Provide
    Arbitrary<Double> longitudes() {
        return Arbitraries.doubles().between(-180.0, 180.0);
    }
}
