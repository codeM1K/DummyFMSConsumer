package com.fms.consumer.ui;

import com.fms.consumer.model.LocationData;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for active vehicle display in the {@link LocationDataGrid} component.
 *
 * <p><b>Property 22: Active Vehicle Display</b></p>
 * <p>"For any set of vehicles with active consumption sessions, the UI SHALL display all
 * actively consumed vehicles in the vehicle list."</p>
 *
 * <p><b>Validates: Requirements 8.1</b></p>
 */
class ActiveVehicleDisplayPropertyTest {

    /**
     * For any set of vehicles that have received location data, the LocationDataGrid
     * SHALL contain a row for each vehicle (identified by vehicleId).
     *
     * <p><b>Validates: Requirements 8.1</b></p>
     */
    @Property(tries = 200)
    void allActiveVehiclesAppearInGrid(
            @ForAll("vehicleLocationDataSets") List<LocationData> locationDataList) {

        LocationDataGrid grid = new LocationDataGrid();

        // Add all location data
        for (LocationData data : locationDataList) {
            grid.addLocationData(data);
        }

        // Collect unique vehicle IDs from the input
        Set<String> expectedVehicleIds = new HashSet<>();
        for (LocationData data : locationDataList) {
            if (data != null && data.getVehicleId() != null) {
                expectedVehicleIds.add(data.getVehicleId());
            }
        }

        // The grid must contain exactly one row per unique vehicle
        assertEquals(expectedVehicleIds.size(), grid.getRowCount(),
                "Grid row count must equal the number of unique active vehicles");

        // Each vehicle must be findable by ID
        for (String vehicleId : expectedVehicleIds) {
            assertNotNull(grid.getRow(vehicleId),
                    "Vehicle '" + vehicleId + "' must appear in the grid");
        }
    }

    /**
     * For any set of vehicles with multiple location updates, the grid SHALL still
     * display exactly one row per vehicle (upsert behavior).
     *
     * <p><b>Validates: Requirements 8.1</b></p>
     */
    @Property(tries = 200)
    void duplicateUpdatesDoNotCreateExtraRows(
            @ForAll("vehicleIdsWithMultipleUpdates") List<LocationData> locationDataList) {

        LocationDataGrid grid = new LocationDataGrid();

        for (LocationData data : locationDataList) {
            grid.addLocationData(data);
        }

        Set<String> uniqueVehicleIds = new HashSet<>();
        for (LocationData data : locationDataList) {
            if (data != null && data.getVehicleId() != null) {
                uniqueVehicleIds.add(data.getVehicleId());
            }
        }

        assertEquals(uniqueVehicleIds.size(), grid.getRowCount(),
                "Grid must have exactly one row per unique vehicle ID regardless of update count");
    }

    // --- Providers ---

    /**
     * Generates 1-10 unique location data entries for distinct vehicles.
     */
    @Provide
    Arbitrary<List<LocationData>> vehicleLocationDataSets() {
        return Arbitraries.integers().between(1, 10).flatMap(count -> {
            List<Arbitrary<LocationData>> dataArbitraries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int idx = i;
                dataArbitraries.add(
                        Arbitraries.doubles().between(-90.0, 90.0).flatMap(lat ->
                                Arbitraries.doubles().between(-180.0, 180.0).map(lon ->
                                        new LocationData("vehicle-" + idx, lat, lon, Instant.now(), new HashMap<>())
                                )
                        )
                );
            }
            return Combinators.combine(dataArbitraries).as(list -> list);
        });
    }

    /**
     * Generates location data with some vehicles sending multiple updates.
     */
    @Provide
    Arbitrary<List<LocationData>> vehicleIdsWithMultipleUpdates() {
        return Arbitraries.integers().between(1, 5).flatMap(vehicleCount ->
                Arbitraries.integers().between(1, 4).flatMap(updatesPerVehicle -> {
                    List<Arbitrary<LocationData>> dataArbitraries = new ArrayList<>();
                    for (int v = 0; v < vehicleCount; v++) {
                        for (int u = 0; u < updatesPerVehicle; u++) {
                            int vIdx = v;
                            dataArbitraries.add(
                                    Arbitraries.doubles().between(-90.0, 90.0).flatMap(lat ->
                                            Arbitraries.doubles().between(-180.0, 180.0).map(lon ->
                                                    new LocationData("vehicle-" + vIdx, lat, lon, Instant.now(), new HashMap<>())
                                            )
                                    )
                            );
                        }
                    }
                    return Combinators.combine(dataArbitraries).as(list -> list);
                })
        );
    }
}
