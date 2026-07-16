package com.fms.consumer.model;

import java.util.Objects;

/**
 * Represents a tracked vehicle within the fleet tracking system.
 */
public class Vehicle {

    private String id;
    private String name;
    private String realmId;
    private VehicleStatus status;

    public Vehicle() {
    }

    public Vehicle(String id, String name, String realmId, VehicleStatus status) {
        this.id = id;
        this.name = name;
        this.realmId = realmId;
        this.status = status;
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

    public String getRealmId() {
        return realmId;
    }

    public void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public void setStatus(VehicleStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vehicle vehicle = (Vehicle) o;
        return Objects.equals(id, vehicle.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Vehicle{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", realmId='" + realmId + '\'' +
                ", status=" + status +
                '}';
    }
}
