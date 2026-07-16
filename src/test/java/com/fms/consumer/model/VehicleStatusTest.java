package com.fms.consumer.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VehicleStatusTest {

    @Test
    void enum_containsAllExpectedValues() {
        VehicleStatus[] values = VehicleStatus.values();
        assertEquals(4, values.length);
    }

    @Test
    void enum_activeValue_exists() {
        assertEquals(VehicleStatus.ACTIVE, VehicleStatus.valueOf("ACTIVE"));
    }

    @Test
    void enum_inactiveValue_exists() {
        assertEquals(VehicleStatus.INACTIVE, VehicleStatus.valueOf("INACTIVE"));
    }

    @Test
    void enum_connectingValue_exists() {
        assertEquals(VehicleStatus.CONNECTING, VehicleStatus.valueOf("CONNECTING"));
    }

    @Test
    void enum_errorValue_exists() {
        assertEquals(VehicleStatus.ERROR, VehicleStatus.valueOf("ERROR"));
    }

    @Test
    void enum_invalidValue_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> VehicleStatus.valueOf("UNKNOWN"));
    }
}
