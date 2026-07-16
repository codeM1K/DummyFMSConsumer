package com.fms.consumer.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a logical grouping of vehicles within the Open Remote system.
 */
public class Realm {

    private String id;
    private String name;
    private List<Vehicle> vehicles;

    public Realm() {
        this.vehicles = new ArrayList<>();
    }

    public Realm(String id, String name, List<Vehicle> vehicles) {
        this.id = id;
        this.name = name;
        this.vehicles = vehicles != null ? vehicles : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Vehicle> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<Vehicle> vehicles) {
        this.vehicles = vehicles != null ? vehicles : new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Realm realm = (Realm) o;
        return Objects.equals(id, realm.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Realm{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", vehicles=" + (vehicles != null ? vehicles.size() : 0) + " vehicles" +
                '}';
    }
}
