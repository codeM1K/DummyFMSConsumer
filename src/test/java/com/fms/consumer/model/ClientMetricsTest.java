package com.fms.consumer.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientMetricsTest {

    @Test
    void defaultConstructor_initializesWithDefaults() {
        ClientMetrics metrics = new ClientMetrics();
        assertNull(metrics.getClientId());
        assertEquals(0, metrics.getConnections());
        assertEquals(0L, metrics.getUpdatesReceived());
        assertEquals(0.0, metrics.getAverageLatency());
    }

    @Test
    void singleArgConstructor_initializesWithZeros() {
        ClientMetrics metrics = new ClientMetrics("client1");
        assertEquals("client1", metrics.getClientId());
        assertEquals(0, metrics.getConnections());
        assertEquals(0L, metrics.getUpdatesReceived());
        assertEquals(0.0, metrics.getAverageLatency());
    }

    @Test
    void singleArgConstructor_nullClientId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new ClientMetrics(null));
    }

    @Test
    void singleArgConstructor_blankClientId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new ClientMetrics("  "));
    }

    @Test
    void parameterizedConstructor_setsAllFields() {
        ClientMetrics metrics = new ClientMetrics("client1", 3, 1500L, 42.5);

        assertEquals("client1", metrics.getClientId());
        assertEquals(3, metrics.getConnections());
        assertEquals(1500L, metrics.getUpdatesReceived());
        assertEquals(42.5, metrics.getAverageLatency(), 0.001);
    }

    @Test
    void parameterizedConstructor_nullClientId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClientMetrics(null, 3, 1500L, 42.5));
    }

    @Test
    void parameterizedConstructor_blankClientId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClientMetrics("", 3, 1500L, 42.5));
    }

    @Test
    void parameterizedConstructor_negativeConnections_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClientMetrics("client1", -1, 1500L, 42.5));
    }

    @Test
    void parameterizedConstructor_negativeUpdatesReceived_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClientMetrics("client1", 3, -1L, 42.5));
    }

    @Test
    void parameterizedConstructor_negativeAverageLatency_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClientMetrics("client1", 3, 1500L, -1.0));
    }

    @Test
    void settersAndGetters_workCorrectly() {
        ClientMetrics metrics = new ClientMetrics();
        metrics.setClientId("client2");
        metrics.setConnections(5);
        metrics.setUpdatesReceived(2500L);
        metrics.setAverageLatency(30.7);

        assertEquals("client2", metrics.getClientId());
        assertEquals(5, metrics.getConnections());
        assertEquals(2500L, metrics.getUpdatesReceived());
        assertEquals(30.7, metrics.getAverageLatency(), 0.001);
    }

    @Test
    void equals_sameClientId_returnsTrue() {
        ClientMetrics m1 = new ClientMetrics("client1", 3, 1500L, 42.5);
        ClientMetrics m2 = new ClientMetrics("client1", 5, 2000L, 50.0);
        assertEquals(m1, m2);
    }

    @Test
    void equals_differentClientId_returnsFalse() {
        ClientMetrics m1 = new ClientMetrics("client1", 3, 1500L, 42.5);
        ClientMetrics m2 = new ClientMetrics("client2", 3, 1500L, 42.5);
        assertNotEquals(m1, m2);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        ClientMetrics m1 = new ClientMetrics("client1", 3, 1500L, 42.5);
        assertNotEquals(m1, null);
    }

    @Test
    void equals_sameObject_returnsTrue() {
        ClientMetrics m1 = new ClientMetrics("client1", 3, 1500L, 42.5);
        assertEquals(m1, m1);
    }

    @Test
    void equals_differentType_returnsFalse() {
        ClientMetrics m1 = new ClientMetrics("client1", 3, 1500L, 42.5);
        assertNotEquals(m1, "not a metric");
    }

    @Test
    void hashCode_sameClientId_returnsSameHash() {
        ClientMetrics m1 = new ClientMetrics("client1", 3, 1500L, 42.5);
        ClientMetrics m2 = new ClientMetrics("client1", 5, 2000L, 50.0);
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void hashCode_differentClientId_returnsDifferentHash() {
        ClientMetrics m1 = new ClientMetrics("client1", 3, 1500L, 42.5);
        ClientMetrics m2 = new ClientMetrics("client2", 3, 1500L, 42.5);
        assertNotEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void toString_returnsNonNull() {
        ClientMetrics metrics = new ClientMetrics("client1", 3, 1500L, 42.5);
        String str = metrics.toString();
        assertNotNull(str);
        assertTrue(str.contains("client1"));
        assertTrue(str.contains("3"));
        assertTrue(str.contains("1500"));
    }

    @Test
    void zeroValues_areValid() {
        ClientMetrics metrics = new ClientMetrics("client1", 0, 0L, 0.0);
        assertEquals(0, metrics.getConnections());
        assertEquals(0L, metrics.getUpdatesReceived());
        assertEquals(0.0, metrics.getAverageLatency());
    }
}
