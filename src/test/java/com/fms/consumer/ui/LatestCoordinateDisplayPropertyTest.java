package com.fms.consumer.ui;

import com.fms.consumer.model.LocationData;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for latest coordinate display in the {@link LocationDataGrid} component.
 *
 * <p><b>Property 23: Latest Coordinate Display</b></p>
 * <p>"For any received Location_Data for a vehicle, the UI SHALL display the latest geographic
 * coordinates for that vehicle."</p>
 *
 * <p><b>Validates: Requirements 8.2</b></p>
 */
class LatestCoordinateDisplayPropertyTest {

    /**
     * For any sequence of location updates for a vehicle, the grid SHALL display
     * the coordinates from the most recently added location data.
     *
     * <p><b>Validates: Requirements 8.2</b></p>
     */
    @Property(tries = 200)
    void gridShowsLatestCoordinatesForVehicle(
            @ForAll("coordinateSequences") List<double[]> coordSequence) {

        Assume.that(!coordSequence.isEmpty());

        LocationDataGrid grid = new LocationDataGrid();
        String vehicleId = "test-vehicle";

        // Send multiple updates for same vehicle
        for (double[] coords : coordSequence) {
            LocationData data = new LocationData(vehicleId, coords[0], coords[1], Instant.now(), new HashMap<>());
            grid.addLocationData(data);
        }

        // The last update should be what's displayed
        double[] lastCoords = coordSequence.get(coordSequence.size() - 1);
        LocationDataGrid.LocationDataRow row = grid.getRow(vehicleId);

        assertNotNull(row, "Row must exist for the vehicle");
        assertEquals(lastCoords[0], row.getLatitude(), 0.0001,
                "Latitude must match the last update sent");
        assertEquals(lastCoords[1], row.getLongitude(), 0.0001,
                "Longitude must match the last update sent");
    }

    /**
     * For any valid latitude and longitude values, the grid SHALL accurately display
     * those coordinates without loss of precision beyond floating point.
     *
     * <p><b>Validates: Requirements 8.2</b></p>
     */
    @Property(tries = 200)
    void coordinatesDisplayedAccurately(
            @ForAll("latitudes") double latitude,
            @ForAll("longitudes") double longitude) {

        LocationDataGrid grid = new LocationDataGrid();
        String vehicleId = "accuracy-test";

        LocationData data = new LocationData(vehicleId, latitude, longitude, Instant.now(), new HashMap<>());
        grid.addLocationData(data);

        LocationDataGrid.LocationDataRow row = grid.getRow(vehicleId);
        assertNotNull(row);
        assertEquals(latitude, row.getLatitude(), 0.0001,
                "Displayed latitude must match input");
        assertEquals(longitude, row.getLongitude(), 0.0001,
                "Displayed longitude must match input");
    }

    // --- Providers ---

    @Provide
    Arbitrary<List<double[]>> coordinateSequences() {
        return Arbitraries.integers().between(1, 8).flatMap(count -> {
            List<Arbitrary<double[]>> coordArbitraries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                coordArbitraries.add(
                        Arbitraries.doubles().between(-90.0, 90.0).flatMap(lat ->
                                Arbitraries.doubles().between(-180.0, 180.0).map(lon ->
                                        new double[]{lat, lon}
                                )
                        )
                );
            }
            return Combinators.combine(coordArbitraries).as(list -> list);
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
