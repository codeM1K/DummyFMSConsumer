package com.fms.consumer.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ConsumptionMetricsTest {

    @Test
    void defaultConstructor_initializesWithDefaults() {
        ConsumptionMetrics metrics = new ConsumptionMetrics();
        assertEquals(0, metrics.getActiveConnections());
        assertEquals(0, metrics.getActiveVehicles());
        assertEquals(0, metrics.getActiveRealms());
        assertEquals(0.0, metrics.getUpdatesPerSecond());
        assertNotNull(metrics.getLastUpdate());
    }

    @Test
    void parameterizedConstructor_setsAllFields() {
        ConsumptionMetrics metrics = new ConsumptionMetrics(5, 10, 3, 25.5);

        assertEquals(5, metrics.getActiveConnections());
        assertEquals(10, metrics.getActiveVehicles());
        assertEquals(3, metrics.getActiveRealms());
        assertEquals(25.5, metrics.getUpdatesPerSecond(), 0.001);
        assertNotNull(metrics.getLastUpdate());
    }

    @Test
    void parameterizedConstructor_negativeConnections_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConsumptionMetrics(-1, 10, 3, 25.5));
    }

    @Test
    void parameterizedConstructor_negativeVehicles_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConsumptionMetrics(5, -1, 3, 25.5));
    }

    @Test
    void parameterizedConstructor_negativeRealms_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConsumptionMetrics(5, 10, -1, 25.5));
    }

    @Test
    void parameterizedConstructor_negativeUpdatesPerSecond_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConsumptionMetrics(5, 10, 3, -1.0));
    }

    @Test
    void settersAndGetters_workCorrectly() {
        ConsumptionMetrics metrics = new ConsumptionMetrics();
        Instant now = Instant.now();

        metrics.setActiveConnections(7);
        metrics.setActiveVehicles(15);
        metrics.setActiveRealms(4);
        metrics.setUpdatesPerSecond(100.3);
        metrics.setLastUpdate(now);

        assertEquals(7, metrics.getActiveConnections());
        assertEquals(15, metrics.getActiveVehicles());
        assertEquals(4, metrics.getActiveRealms());
        assertEquals(100.3, metrics.getUpdatesPerSecond(), 0.001);
        assertEquals(now, metrics.getLastUpdate());
    }

    @Test
    void equals_sameFields_returnsTrue() {
        ConsumptionMetrics m1 = new ConsumptionMetrics();
        m1.setActiveConnections(5);
        m1.setActiveVehicles(10);
        m1.setActiveRealms(3);
        m1.setUpdatesPerSecond(25.5);
        Instant fixedInstant = Instant.parse("2024-01-01T00:00:00Z");
        m1.setLastUpdate(fixedInstant);

        ConsumptionMetrics m2 = new ConsumptionMetrics();
        m2.setActiveConnections(5);
        m2.setActiveVehicles(10);
        m2.setActiveRealms(3);
        m2.setUpdatesPerSecond(25.5);
        m2.setLastUpdate(fixedInstant);

        assertEquals(m1, m2);
    }

    @Test
    void equals_differentFields_returnsFalse() {
        ConsumptionMetrics m1 = new ConsumptionMetrics(5, 10, 3, 25.5);
        ConsumptionMetrics m2 = new ConsumptionMetrics(6, 10, 3, 25.5);
        assertNotEquals(m1, m2);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        ConsumptionMetrics m1 = new ConsumptionMetrics(5, 10, 3, 25.5);
        assertNotEquals(m1, null);
    }

    @Test
    void equals_sameObject_returnsTrue() {
        ConsumptionMetrics m1 = new ConsumptionMetrics(5, 10, 3, 25.5);
        assertEquals(m1, m1);
    }

    @Test
    void hashCode_consistentWithEquals() {
        ConsumptionMetrics m1 = new ConsumptionMetrics();
        m1.setActiveConnections(5);
        m1.setActiveVehicles(10);
        m1.setActiveRealms(3);
        m1.setUpdatesPerSecond(25.5);
        Instant fixedInstant = Instant.parse("2024-01-01T00:00:00Z");
        m1.setLastUpdate(fixedInstant);

        ConsumptionMetrics m2 = new ConsumptionMetrics();
        m2.setActiveConnections(5);
        m2.setActiveVehicles(10);
        m2.setActiveRealms(3);
        m2.setUpdatesPerSecond(25.5);
        m2.setLastUpdate(fixedInstant);

        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void toString_returnsNonNull() {
        ConsumptionMetrics metrics = new ConsumptionMetrics(5, 10, 3, 25.5);
        String str = metrics.toString();
        assertNotNull(str);
        assertTrue(str.contains("5"));
        assertTrue(str.contains("10"));
        assertTrue(str.contains("3"));
    }

    @Test
    void zeroValues_areValid() {
        ConsumptionMetrics metrics = new ConsumptionMetrics(0, 0, 0, 0.0);
        assertEquals(0, metrics.getActiveConnections());
        assertEquals(0, metrics.getActiveVehicles());
        assertEquals(0, metrics.getActiveRealms());
        assertEquals(0.0, metrics.getUpdatesPerSecond());
        assertNotNull(metrics.getLastUpdate());
    }
}
