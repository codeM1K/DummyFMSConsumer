package com.fms.consumer.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents aggregate consumption metrics across all active sessions.
 */
public class ConsumptionMetrics {

    private int activeConnections;
    private int activeVehicles;
    private int activeRealms;
    private double updatesPerSecond;
    private Instant lastUpdate;

    public ConsumptionMetrics() {
        this.lastUpdate = Instant.now();
    }

    public ConsumptionMetrics(int activeConnections, int activeVehicles, int activeRealms, double updatesPerSecond) {
        if (activeConnections < 0) {
            throw new IllegalArgumentException("activeConnections must not be negative");
        }
        if (activeVehicles < 0) {
            throw new IllegalArgumentException("activeVehicles must not be negative");
        }
        if (activeRealms < 0) {
            throw new IllegalArgumentException("activeRealms must not be negative");
        }
        if (updatesPerSecond < 0) {
            throw new IllegalArgumentException("updatesPerSecond must not be negative");
        }
        this.activeConnections = activeConnections;
        this.activeVehicles = activeVehicles;
        this.activeRealms = activeRealms;
        this.updatesPerSecond = updatesPerSecond;
        this.lastUpdate = Instant.now();
    }

    public int getActiveConnections() {
        return activeConnections;
    }

    public void setActiveConnections(int activeConnections) {
        this.activeConnections = activeConnections;
    }

    public int getActiveVehicles() {
        return activeVehicles;
    }

    public void setActiveVehicles(int activeVehicles) {
        this.activeVehicles = activeVehicles;
    }

    public int getActiveRealms() {
        return activeRealms;
    }

    public void setActiveRealms(int activeRealms) {
        this.activeRealms = activeRealms;
    }

    public double getUpdatesPerSecond() {
        return updatesPerSecond;
    }

    public void setUpdatesPerSecond(double updatesPerSecond) {
        this.updatesPerSecond = updatesPerSecond;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConsumptionMetrics that = (ConsumptionMetrics) o;
        return activeConnections == that.activeConnections &&
                activeVehicles == that.activeVehicles &&
                activeRealms == that.activeRealms &&
                Double.compare(that.updatesPerSecond, updatesPerSecond) == 0 &&
                Objects.equals(lastUpdate, that.lastUpdate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(activeConnections, activeVehicles, activeRealms, updatesPerSecond, lastUpdate);
    }

    @Override
    public String toString() {
        return "ConsumptionMetrics{" +
                "activeConnections=" + activeConnections +
                ", activeVehicles=" + activeVehicles +
                ", activeRealms=" + activeRealms +
                ", updatesPerSecond=" + updatesPerSecond +
                ", lastUpdate=" + lastUpdate +
                '}';
    }
}
