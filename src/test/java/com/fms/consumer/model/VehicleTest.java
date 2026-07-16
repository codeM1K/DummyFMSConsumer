package com.fms.consumer.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VehicleTest {

    @Test
    void defaultConstructor_initializesWithNulls() {
        Vehicle vehicle = new Vehicle();
        assertNull(vehicle.getId());
        assertNull(vehicle.getName());
        assertNull(vehicle.getRealmId());
        assertNull(vehicle.getStatus());
    }

    @Test
    void parameterizedConstructor_setsAllFields() {
        Vehicle vehicle = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);

        assertEquals("v1", vehicle.getId());
        assertEquals("Truck1", vehicle.getName());
        assertEquals("r1", vehicle.getRealmId());
        assertEquals(VehicleStatus.ACTIVE, vehicle.getStatus());
    }

    @Test
    void settersAndGetters_workCorrectly() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId("v2");
        vehicle.setName("Car1");
        vehicle.setRealmId("r2");
        vehicle.setStatus(VehicleStatus.CONNECTING);

        assertEquals("v2", vehicle.getId());
        assertEquals("Car1", vehicle.getName());
        assertEquals("r2", vehicle.getRealmId());
        assertEquals(VehicleStatus.CONNECTING, vehicle.getStatus());
    }

    @Test
    void equals_sameId_returnsTrue() {
        Vehicle v1 = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        Vehicle v2 = new Vehicle("v1", "DifferentName", "r2", VehicleStatus.INACTIVE);
        assertEquals(v1, v2);
    }

    @Test
    void equals_differentId_returnsFalse() {
        Vehicle v1 = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        Vehicle v2 = new Vehicle("v2", "Truck1", "r1", VehicleStatus.ACTIVE);
        assertNotEquals(v1, v2);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        Vehicle v1 = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        assertNotEquals(v1, null);
    }

    @Test
    void equals_sameObject_returnsTrue() {
        Vehicle v1 = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        assertEquals(v1, v1);
    }

    @Test
    void equals_differentType_returnsFalse() {
        Vehicle v1 = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        assertNotEquals(v1, "not a vehicle");
    }

    @Test
    void hashCode_sameId_returnsSameHash() {
        Vehicle v1 = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        Vehicle v2 = new Vehicle("v1", "Truck2", "r2", VehicleStatus.INACTIVE);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void hashCode_differentId_returnsDifferentHash() {
        Vehicle v1 = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        Vehicle v2 = new Vehicle("v2", "Truck1", "r1", VehicleStatus.ACTIVE);
        assertNotEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void toString_returnsNonNull() {
        Vehicle vehicle = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        String str = vehicle.toString();
        assertNotNull(str);
        assertTrue(str.contains("v1"));
        assertTrue(str.contains("Truck1"));
        assertTrue(str.contains("r1"));
        assertTrue(str.contains("ACTIVE"));
    }

    @Test
    void nullId_handledGracefully() {
        Vehicle vehicle = new Vehicle(null, "Truck1", "r1", VehicleStatus.ACTIVE);
        assertNull(vehicle.getId());
        assertNotNull(vehicle.toString());
    }

    @Test
    void emptyName_handledGracefully() {
        Vehicle vehicle = new Vehicle("v1", "", "r1", VehicleStatus.ACTIVE);
        assertEquals("", vehicle.getName());
    }

    @Test
    void statusTransitions_workCorrectly() {
        Vehicle vehicle = new Vehicle("v1", "Truck1", "r1", VehicleStatus.INACTIVE);
        assertEquals(VehicleStatus.INACTIVE, vehicle.getStatus());

        vehicle.setStatus(VehicleStatus.CONNECTING);
        assertEquals(VehicleStatus.CONNECTING, vehicle.getStatus());

        vehicle.setStatus(VehicleStatus.ACTIVE);
        assertEquals(VehicleStatus.ACTIVE, vehicle.getStatus());

        vehicle.setStatus(VehicleStatus.ERROR);
        assertEquals(VehicleStatus.ERROR, vehicle.getStatus());
    }
}
