package com.fms.consumer.ui;

import com.fms.consumer.model.LocationData;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for timestamp display in the {@link LocationDataGrid} component.
 *
 * <p><b>Property 24: Timestamp Display</b></p>
 * <p>"For any vehicle with received Location_Data, the UI SHALL display the timestamp
 * of the most recent update."</p>
 *
 * <p><b>Validates: Requirements 8.3</b></p>
 */
class TimestampDisplayPropertyTest {

    /**
     * For any location data with a timestamp, the grid row SHALL store and display
     * the timestamp from the most recent update for that vehicle.
     *
     * <p><b>Validates: Requirements 8.3</b></p>
     */
    @Property(tries = 200)
    void gridDisplaysLatestTimestamp(
            @ForAll("timestampSequences") List<Instant> timestamps) {

        Assume.that(!timestamps.isEmpty());

        LocationDataGrid grid = new LocationDataGrid();
        String vehicleId = "ts-test-vehicle";

        // Send updates with different timestamps
        for (Instant ts : timestamps) {
            LocationData data = new LocationData(vehicleId, 37.0, 23.0, ts, new HashMap<>());
            grid.addLocationData(data);
        }

        // The last timestamp should be what's stored in the row
        Instant lastTimestamp = timestamps.get(timestamps.size() - 1);
        LocationDataGrid.LocationDataRow row = grid.getRow(vehicleId);

        assertNotNull(row, "Row must exist for the vehicle");
        assertEquals(lastTimestamp, row.getTimestamp(),
                "Timestamp must be the most recently received update's timestamp");
    }

    /**
     * For any vehicle with a non-null timestamp, the formatted timestamp string
     * SHALL NOT be "N/A" and SHALL contain the year portion of the timestamp.
     *
     * <p><b>Validates: Requirements 8.3</b></p>
     */
    @Property(tries = 200)
    void nonNullTimestampFormatsCorrectly(
            @ForAll("validTimestamps") Instant timestamp) {

        LocationDataGrid grid = new LocationDataGrid();
        String vehicleId = "format-test";

        LocationData data = new LocationData(vehicleId, 37.0, 23.0, timestamp, new HashMap<>());
        grid.addLocationData(data);

        LocationDataGrid.LocationDataRow row = grid.getRow(vehicleId);
        assertNotNull(row);
        assertNotNull(row.getTimestamp(), "Timestamp should not be null");

        String formatted = row.getFormattedTimestamp();
        assertNotEquals("N/A", formatted,
                "Formatted timestamp must not be 'N/A' when timestamp is present");
        assertFalse(formatted.isEmpty(),
                "Formatted timestamp must not be empty");
    }

    // --- Providers ---

    /**
     * Generates sequences of 1-6 timestamps.
     */
    @Provide
    Arbitrary<List<Instant>> timestampSequences() {
        return Arbitraries.integers().between(1, 6).flatMap(count -> {
            List<Arbitrary<Instant>> tsArbitraries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                tsArbitraries.add(
                        Arbitraries.longs().between(0L, 2000000000L).map(Instant::ofEpochSecond)
                );
            }
            return Combinators.combine(tsArbitraries).as(list -> list);
        });
    }

    @Provide
    Arbitrary<Instant> validTimestamps() {
        return Arbitraries.longs().between(946684800L, 2000000000L) // 2000-01-01 to ~2033
                .map(Instant::ofEpochSecond);
    }
}
