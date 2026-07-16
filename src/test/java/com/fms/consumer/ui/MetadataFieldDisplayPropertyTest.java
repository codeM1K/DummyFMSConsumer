package com.fms.consumer.ui;

import com.fms.consumer.model.LocationData;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for metadata field display in the {@link LocationDataGrid} component.
 *
 * <p><b>Property 26: Metadata Field Display</b></p>
 * <p>"For any Location_Data containing additional metadata fields, the UI SHALL display
 * the relevant metadata fields for that vehicle."</p>
 *
 * <p><b>Validates: Requirements 8.5</b></p>
 */
class MetadataFieldDisplayPropertyTest {

    /**
     * For any location data with metadata key-value pairs, the grid row SHALL store
     * all metadata fields and make them accessible for display.
     *
     * <p><b>Validates: Requirements 8.5</b></p>
     */
    @Property(tries = 200)
    void gridRowContainsAllMetadataFields(
            @ForAll("metadataEntries") Map<String, Object> metadata) {

        LocationDataGrid grid = new LocationDataGrid();
        String vehicleId = "meta-vehicle";

        LocationData data = new LocationData(vehicleId, 37.0, 23.0, Instant.now(), metadata);
        grid.addLocationData(data);

        LocationDataGrid.LocationDataRow row = grid.getRow(vehicleId);
        assertNotNull(row, "Row must exist");

        Map<String, Object> displayedMetadata = row.getMetadata();
        assertNotNull(displayedMetadata, "Row metadata must not be null");

        // All metadata keys from the input should be in the row's metadata
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            assertTrue(displayedMetadata.containsKey(entry.getKey()),
                    "Metadata key '" + entry.getKey() + "' must be present in the row");
            assertEquals(entry.getValue(), displayedMetadata.get(entry.getKey()),
                    "Metadata value for key '" + entry.getKey() + "' must match input");
        }
    }

    /**
     * For any update to a vehicle's location data with new metadata, the grid row
     * SHALL reflect the latest metadata from the most recent update.
     *
     * <p><b>Validates: Requirements 8.5</b></p>
     */
    @Property(tries = 200)
    void metadataUpdatedOnSubsequentLocationData(
            @ForAll("metadataEntries") Map<String, Object> firstMeta,
            @ForAll("metadataEntries") Map<String, Object> secondMeta) {

        LocationDataGrid grid = new LocationDataGrid();
        String vehicleId = "meta-update-vehicle";

        // First update
        LocationData first = new LocationData(vehicleId, 37.0, 23.0, Instant.now(), firstMeta);
        grid.addLocationData(first);

        // Second update with different metadata
        LocationData second = new LocationData(vehicleId, 38.0, 24.0, Instant.now(), secondMeta);
        grid.addLocationData(second);

        LocationDataGrid.LocationDataRow row = grid.getRow(vehicleId);
        assertNotNull(row);

        Map<String, Object> displayedMetadata = row.getMetadata();
        // The row should now contain the second update's metadata
        for (Map.Entry<String, Object> entry : secondMeta.entrySet()) {
            assertTrue(displayedMetadata.containsKey(entry.getKey()),
                    "After update, metadata key '" + entry.getKey() + "' must be present");
            assertEquals(entry.getValue(), displayedMetadata.get(entry.getKey()),
                    "After update, metadata value for key '" + entry.getKey() + "' must match latest");
        }
    }

    /**
     * For any location data with null metadata, the grid row SHALL have an empty
     * (non-null) metadata map.
     *
     * <p><b>Validates: Requirements 8.5</b></p>
     */
    @Property(tries = 50)
    void nullMetadataResultsInEmptyMap(
            @ForAll("vehicleIds") String vehicleId) {

        LocationDataGrid grid = new LocationDataGrid();

        LocationData data = new LocationData(vehicleId, 37.0, 23.0, Instant.now(), null);
        grid.addLocationData(data);

        LocationDataGrid.LocationDataRow row = grid.getRow(vehicleId);
        assertNotNull(row);
        assertNotNull(row.getMetadata(), "Metadata map must not be null even when input metadata is null");
    }

    // --- Providers ---

    @Provide
    Arbitrary<Map<String, Object>> metadataEntries() {
        return Arbitraries.integers().between(0, 5).flatMap(count -> {
            if (count == 0) {
                return Arbitraries.just(new HashMap<>());
            }
            Arbitrary<String> keys = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10);
            Arbitrary<Object> values = Arbitraries.oneOf(
                    Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20).map(s -> (Object) s),
                    Arbitraries.integers().between(0, 1000).map(i -> (Object) i),
                    Arbitraries.doubles().between(0.0, 100.0).map(d -> (Object) d)
            );
            return Arbitraries.maps(keys, values).ofMinSize(count).ofMaxSize(count);
        });
    }

    @Provide
    Arbitrary<String> vehicleIds() {
        return Arbitraries.integers().between(1, 100).map(i -> "vehicle-" + i);
    }
}
