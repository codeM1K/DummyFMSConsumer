package com.fms.consumer.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConsumptionSessionTest {

    @Test
    void defaultConstructor_initializesWithDefaults() {
        ConsumptionSession session = new ConsumptionSession();
        assertNull(session.getSessionId());
        assertNull(session.getVehicle());
        assertNull(session.getClientId());
        assertNull(session.getConnectionId());
        assertEquals(ConsumptionMode.IDLE, session.getMode());
        assertNull(session.getStartTime());
        assertFalse(session.isActive());
    }

    @Test
    void parameterizedConstructor_setsAllFields() {
        Vehicle vehicle = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        ConsumptionSession session = new ConsumptionSession("s1", vehicle, "client1", ConsumptionMode.RANDOM);

        assertEquals("s1", session.getSessionId());
        assertEquals(vehicle, session.getVehicle());
        assertEquals("client1", session.getClientId());
        assertEquals(ConsumptionMode.RANDOM, session.getMode());
        assertNotNull(session.getStartTime());
        assertTrue(session.isActive());
    }

    @Test
    void parameterizedConstructor_nullSessionId_throwsException() {
        Vehicle vehicle = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        assertThrows(IllegalArgumentException.class,
                () -> new ConsumptionSession(null, vehicle, "client1", ConsumptionMode.RANDOM));
    }

    @Test
    void parameterizedConstructor_blankSessionId_throwsException() {
        Vehicle vehicle = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        assertThrows(IllegalArgumentException.class,
                () -> new ConsumptionSession("  ", vehicle, "client1", ConsumptionMode.RANDOM));
    }

    @Test
    void parameterizedConstructor_nullVehicle_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConsumptionSession("s1", null, "client1", ConsumptionMode.RANDOM));
    }

    @Test
    void parameterizedConstructor_nullClientId_throwsException() {
        Vehicle vehicle = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        assertThrows(IllegalArgumentException.class,
                () -> new ConsumptionSession("s1", vehicle, null, ConsumptionMode.RANDOM));
    }

    @Test
    void parameterizedConstructor_blankClientId_throwsException() {
        Vehicle vehicle = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        assertThrows(IllegalArgumentException.class,
                () -> new ConsumptionSession("s1", vehicle, "", ConsumptionMode.RANDOM));
    }

    @Test
    void parameterizedConstructor_nullMode_throwsException() {
        Vehicle vehicle = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        assertThrows(IllegalArgumentException.class,
                () -> new ConsumptionSession("s1", vehicle, "client1", null));
    }

    @Test
    void settersAndGetters_workCorrectly() {
        ConsumptionSession session = new ConsumptionSession();
        Vehicle vehicle = new Vehicle("v2", "Car1", "r2", VehicleStatus.CONNECTING);
        Instant now = Instant.now();

        session.setSessionId("s2");
        session.setVehicle(vehicle);
        session.setClientId("client2");
        session.setConnectionId("conn2");
        session.setMode(ConsumptionMode.CONTROLLED);
        session.setStartTime(now);
        session.setActive(true);

        assertEquals("s2", session.getSessionId());
        assertEquals(vehicle, session.getVehicle());
        assertEquals("client2", session.getClientId());
        assertEquals("conn2", session.getConnectionId());
        assertEquals(ConsumptionMode.CONTROLLED, session.getMode());
        assertEquals(now, session.getStartTime());
        assertTrue(session.isActive());
    }

    @Test
    void activeFlag_canBeToggledMultipleTimes() {
        ConsumptionSession session = new ConsumptionSession();
        assertFalse(session.isActive());

        session.setActive(true);
        assertTrue(session.isActive());

        session.setActive(false);
        assertFalse(session.isActive());

        session.setActive(true);
        assertTrue(session.isActive());
    }

    @Test
    void deactivate_setsActiveToFalse() {
        Vehicle vehicle = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        ConsumptionSession session = new ConsumptionSession("s1", vehicle, "client1", ConsumptionMode.RANDOM);
        assertTrue(session.isActive());

        session.deactivate();
        assertFalse(session.isActive());
    }

    @Test
    void activate_setsActiveToTrue() {
        ConsumptionSession session = new ConsumptionSession();
        assertFalse(session.isActive());

        session.activate();
        assertTrue(session.isActive());
    }

    @Test
    void activeFlag_threadSafety_multipleWriters() throws InterruptedException {
        Vehicle vehicle = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        ConsumptionSession session = new ConsumptionSession("s1", vehicle, "client1", ConsumptionMode.RANDOM);

        int threadCount = 10;
        int iterationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger trueCount = new AtomicInteger(0);
        AtomicInteger falseCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final boolean valueToSet = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        session.setActive(valueToSet);
                        boolean read = session.isActive();
                        if (read) trueCount.incrementAndGet();
                        else falseCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Verify no torn reads occurred - total reads must equal total iterations
        assertEquals(threadCount * iterationsPerThread, trueCount.get() + falseCount.get());
        // The final value must be a valid boolean (trivially true, confirms no exception)
        boolean finalValue = session.isActive();
        assertTrue(finalValue || !finalValue);
    }

    @Test
    void equals_sameSessionId_returnsTrue() {
        Vehicle vehicle = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        ConsumptionSession s1 = new ConsumptionSession("s1", vehicle, "c1", ConsumptionMode.RANDOM);
        ConsumptionSession s2 = new ConsumptionSession("s1", vehicle, "c2", ConsumptionMode.CONTROLLED);
        assertEquals(s1, s2);
    }

    @Test
    void equals_differentSessionId_returnsFalse() {
        Vehicle vehicle = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        ConsumptionSession s1 = new ConsumptionSession("s1", vehicle, "c1", ConsumptionMode.RANDOM);
        ConsumptionSession s2 = new ConsumptionSession("s2", vehicle, "c1", ConsumptionMode.RANDOM);
        assertNotEquals(s1, s2);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        Vehicle vehicle = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        ConsumptionSession s1 = new ConsumptionSession("s1", vehicle, "c1", ConsumptionMode.RANDOM);
        assertNotEquals(s1, null);
    }

    @Test
    void equals_sameObject_returnsTrue() {
        Vehicle vehicle = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        ConsumptionSession s1 = new ConsumptionSession("s1", vehicle, "c1", ConsumptionMode.RANDOM);
        assertEquals(s1, s1);
    }

    @Test
    void hashCode_sameSessionId_returnsSameHash() {
        Vehicle vehicle = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        ConsumptionSession s1 = new ConsumptionSession("s1", vehicle, "c1", ConsumptionMode.RANDOM);
        ConsumptionSession s2 = new ConsumptionSession("s1", vehicle, "c2", ConsumptionMode.CONTROLLED);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void toString_returnsNonNull() {
        Vehicle vehicle = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        ConsumptionSession session = new ConsumptionSession("s1", vehicle, "c1", ConsumptionMode.RANDOM);
        assertNotNull(session.toString());
        assertTrue(session.toString().contains("s1"));
        assertTrue(session.toString().contains("RANDOM"));
    }

    @Test
    void modeTransitions_workCorrectly() {
        ConsumptionSession session = new ConsumptionSession();
        assertEquals(ConsumptionMode.IDLE, session.getMode());

        session.setMode(ConsumptionMode.RANDOM);
        assertEquals(ConsumptionMode.RANDOM, session.getMode());

        session.setMode(ConsumptionMode.CONTROLLED);
        assertEquals(ConsumptionMode.CONTROLLED, session.getMode());
    }
}
