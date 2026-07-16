package com.fms.consumer.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConsumptionModeTest {

    @Test
    void enum_containsAllExpectedValues() {
        ConsumptionMode[] values = ConsumptionMode.values();
        assertEquals(3, values.length);
    }

    @Test
    void enum_randomValue_exists() {
        assertEquals(ConsumptionMode.RANDOM, ConsumptionMode.valueOf("RANDOM"));
    }

    @Test
    void enum_controlledValue_exists() {
        assertEquals(ConsumptionMode.CONTROLLED, ConsumptionMode.valueOf("CONTROLLED"));
    }

    @Test
    void enum_idleValue_exists() {
        assertEquals(ConsumptionMode.IDLE, ConsumptionMode.valueOf("IDLE"));
    }

    @Test
    void enum_invalidValue_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> ConsumptionMode.valueOf("UNKNOWN"));
    }
}
