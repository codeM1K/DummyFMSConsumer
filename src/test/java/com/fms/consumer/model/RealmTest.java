package com.fms.consumer.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RealmTest {

    @Test
    void defaultConstructor_initializesEmptyVehicleList() {
        Realm realm = new Realm();
        assertNull(realm.getId());
        assertNull(realm.getName());
        assertNotNull(realm.getVehicles());
        assertTrue(realm.getVehicles().isEmpty());
    }

    @Test
    void parameterizedConstructor_setsAllFields() {
        Vehicle v1 = new Vehicle("v1", "Truck1", "r1", VehicleStatus.ACTIVE);
        List<Vehicle> vehicles = new ArrayList<>(List.of(v1));
        Realm realm = new Realm("r1", "TestRealm", vehicles);

        assertEquals("r1", realm.getId());
        assertEquals("TestRealm", realm.getName());
        assertEquals(1, realm.getVehicles().size());
        assertEquals(v1, realm.getVehicles().get(0));
    }

    @Test
    void parameterizedConstructor_withNullVehicles_initializesEmptyList() {
        Realm realm = new Realm("r1", "TestRealm", null);
        assertNotNull(realm.getVehicles());
        assertTrue(realm.getVehicles().isEmpty());
    }

    @Test
    void settersAndGetters_workCorrectly() {
        Realm realm = new Realm();
        realm.setId("r2");
        realm.setName("Realm2");
        Vehicle v = new Vehicle("v1", "Car1", "r2", VehicleStatus.INACTIVE);
        realm.setVehicles(List.of(v));

        assertEquals("r2", realm.getId());
        assertEquals("Realm2", realm.getName());
        assertEquals(1, realm.getVehicles().size());
    }

    @Test
    void setVehicles_withNull_initializesEmptyList() {
        Realm realm = new Realm("r1", "Test", List.of());
        realm.setVehicles(null);
        assertNotNull(realm.getVehicles());
        assertTrue(realm.getVehicles().isEmpty());
    }

    @Test
    void equals_sameId_returnsTrue() {
        Realm r1 = new Realm("r1", "Realm1", List.of());
        Realm r2 = new Realm("r1", "DifferentName", List.of());
        assertEquals(r1, r2);
    }

    @Test
    void equals_differentId_returnsFalse() {
        Realm r1 = new Realm("r1", "Realm1", List.of());
        Realm r2 = new Realm("r2", "Realm1", List.of());
        assertNotEquals(r1, r2);
    }

    @Test
    void equals_nullObject_returnsFalse() {
        Realm r1 = new Realm("r1", "Realm1", List.of());
        assertNotEquals(r1, null);
    }

    @Test
    void equals_sameObject_returnsTrue() {
        Realm r1 = new Realm("r1", "Realm1", List.of());
        assertEquals(r1, r1);
    }

    @Test
    void equals_differentType_returnsFalse() {
        Realm r1 = new Realm("r1", "Realm1", List.of());
        assertNotEquals(r1, "not a realm");
    }

    @Test
    void hashCode_sameId_returnsSameHash() {
        Realm r1 = new Realm("r1", "Realm1", List.of());
        Realm r2 = new Realm("r1", "Realm2", List.of());
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void hashCode_differentId_returnsDifferentHash() {
        Realm r1 = new Realm("r1", "Realm1", List.of());
        Realm r2 = new Realm("r2", "Realm2", List.of());
        assertNotEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void toString_returnsNonNull() {
        Realm realm = new Realm("r1", "TestRealm", List.of());
        assertNotNull(realm.toString());
        assertTrue(realm.toString().contains("r1"));
        assertTrue(realm.toString().contains("TestRealm"));
    }

    @Test
    void nullId_handledGracefully() {
        Realm realm = new Realm(null, "TestRealm", List.of());
        assertNull(realm.getId());
        assertNotNull(realm.toString());
    }

    @Test
    void emptyName_handledGracefully() {
        Realm realm = new Realm("r1", "", List.of());
        assertEquals("", realm.getName());
    }
}
