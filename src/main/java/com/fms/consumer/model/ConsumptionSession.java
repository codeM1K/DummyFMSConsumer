package com.fms.consumer.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents an active period during which the application consumes location data
 * for a specific vehicle via a WebSocket connection.
 */
public class ConsumptionSession {

    private String sessionId;
    private Vehicle vehicle;
    private String clientId;
    private String connectionId; // Placeholder for WebSocketConnection (created in later task)
    private ConsumptionMode mode;
    private Instant startTime;
    private volatile boolean active;

    public ConsumptionSession() {
        this.active = false;
        this.mode = ConsumptionMode.IDLE;
    }

    public ConsumptionSession(String sessionId, Vehicle vehicle, String clientId, ConsumptionMode mode) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be null or blank");
        }
        if (vehicle == null) {
            throw new IllegalArgumentException("vehicle must not be null");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be null or blank");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        this.sessionId = sessionId;
        this.vehicle = vehicle;
        this.clientId = clientId;
        this.mode = mode;
        this.startTime = Instant.now();
        this.active = true;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public ConsumptionMode getMode() {
        return mode;
    }

    public void setMode(ConsumptionMode mode) {
        this.mode = mode;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    /**
     * Returns whether this session is currently active.
     * This method is thread-safe due to the volatile field.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Sets whether this session is currently active.
     * This method is thread-safe due to the volatile field.
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Deactivates this session, marking it as no longer consuming data.
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Activates this session, marking it as consuming data.
     */
    public void activate() {
        this.active = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConsumptionSession that = (ConsumptionSession) o;
        return Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }

    @Override
    public String toString() {
        return "ConsumptionSession{" +
                "sessionId='" + sessionId + '\'' +
                ", vehicle=" + vehicle +
                ", clientId='" + clientId + '\'' +
                ", mode=" + mode +
                ", startTime=" + startTime +
                ", active=" + active +
                '}';
    }
}
