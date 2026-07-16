package com.fms.consumer.ui;

import com.fms.consumer.model.LocationData;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for realm association display in the {@link LocationDataGrid} component.
 *
 * <p><b>Property 25: Realm Association Display</b></p>
 * <p>"For any vehicle in the UI, the system SHALL display the realm to which that vehicle belongs."</p>
 *
 * <p><b>Validates: Requirements 8.4</b></p>
 */
class RealmAssociationDisplayPropertyTest {

    /**
     * For any vehicle whose location data contains a realmId in the metadata,
     * the grid row SHALL display the correct realm association.
     *
     * <p><b>Validates: Requirements 8.4</b></p>
     */
    @Property(tries = 200)
    void gridDisplaysRealmIdFromMetadata(
            @ForAll("vehicleWithRealm") VehicleRealmPair pair) {

        LocationDataGrid grid = new LocationDataGrid();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("realmId", pair.realmId);
        metadata.put("vehicleName", pair.vehicleName);

        LocationData data = new LocationData(pair.vehicleId, 37.0, 23.0, Instant.now(), metadata);
        grid.addLocationData(data);

        LocationDataGrid.LocationDataRow row = grid.getRow(pair.vehicleId);
        assertNotNull(row, "Row must exist for vehicle " + pair.vehicleId);
        assertEquals(pair.realmId, row.getRealmId(),
                "Grid row must display the realm ID from the location data metadata");
    }

    /**
     * For any number of vehicles with realm associations, each vehicle row SHALL show
     * its own realm ID independently (no cross-contamination between vehicles).
     *
     * <p><b>Validates: Requirements 8.4</b></p>
     */
    @Property(tries = 200)
    void eachVehicleShowsItsOwnRealmAssociation(
            @ForAll("multipleVehiclesWithRealms") List<VehicleRealmPair> pairs) {

        LocationDataGrid grid = new LocationDataGrid();

        for (VehicleRealmPair pair : pairs) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("realmId", pair.realmId);
            metadata.put("vehicleName", pair.vehicleName);

            LocationData data = new LocationData(pair.vehicleId, 37.0, 23.0, Instant.now(), metadata);
            grid.addLocationData(data);
        }

        // Verify each vehicle shows its correct realm
        for (VehicleRealmPair pair : pairs) {
            LocationDataGrid.LocationDataRow row = grid.getRow(pair.vehicleId);
            assertNotNull(row, "Row must exist for vehicle " + pair.vehicleId);
            assertEquals(pair.realmId, row.getRealmId(),
                    "Vehicle '" + pair.vehicleId + "' must show realm '" + pair.realmId + "'");
        }
    }

    // --- Helper record ---

    static class VehicleRealmPair {
        final String vehicleId;
        final String vehicleName;
        final String realmId;

        VehicleRealmPair(String vehicleId, String vehicleName, String realmId) {
            this.vehicleId = vehicleId;
            this.vehicleName = vehicleName;
            this.realmId = realmId;
        }
    }

    // --- Providers ---

    @Provide
    Arbitrary<VehicleRealmPair> vehicleWithRealm() {
        return Arbitraries.integers().between(0, 99).flatMap(idx ->
                Arbitraries.integers().between(0, 9).map(realmIdx ->
                        new VehicleRealmPair(
                                "vehicle-" + idx,
                                "Vehicle " + idx,
                                "realm-" + realmIdx
                        )
                )
        );
    }

    @Provide
    Arbitrary<List<VehicleRealmPair>> multipleVehiclesWithRealms() {
        return Arbitraries.integers().between(1, 8).flatMap(count -> {
            List<Arbitrary<VehicleRealmPair>> pairArbitraries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int idx = i;
                pairArbitraries.add(
                        Arbitraries.integers().between(0, 4).map(realmIdx ->
                                new VehicleRealmPair(
                                        "vehicle-" + idx,
                                        "Vehicle " + idx,
                                        "realm-" + realmIdx
                                )
                        )
                );
            }
            return Combinators.combine(pairArbitraries).as(list -> list);
        });
    }
}
