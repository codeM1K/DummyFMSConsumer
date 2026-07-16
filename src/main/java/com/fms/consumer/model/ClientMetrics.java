package com.fms.consumer.model;

import java.util.Objects;

/**
 * Represents per-client metrics in multi-client simulation mode.
 */
public class ClientMetrics {

    private String clientId;
    private int connections;
    private long updatesReceived;
    private double averageLatency;

    public ClientMetrics() {
    }

    public ClientMetrics(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be null or blank");
        }
        this.clientId = clientId;
        this.connections = 0;
        this.updatesReceived = 0;
        this.averageLatency = 0.0;
    }

    public ClientMetrics(String clientId, int connections, long updatesReceived, double averageLatency) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be null or blank");
        }
        if (connections < 0) {
            throw new IllegalArgumentException("connections must not be negative");
        }
        if (updatesReceived < 0) {
            throw new IllegalArgumentException("updatesReceived must not be negative");
        }
        if (averageLatency < 0) {
            throw new IllegalArgumentException("averageLatency must not be negative");
        }
        this.clientId = clientId;
        this.connections = connections;
        this.updatesReceived = updatesReceived;
        this.averageLatency = averageLatency;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public int getConnections() {
        return connections;
    }

    public void setConnections(int connections) {
        this.connections = connections;
    }

    public long getUpdatesReceived() {
        return updatesReceived;
    }

    public void setUpdatesReceived(long updatesReceived) {
        this.updatesReceived = updatesReceived;
    }

    public double getAverageLatency() {
        return averageLatency;
    }

    public void setAverageLatency(double averageLatency) {
        this.averageLatency = averageLatency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientMetrics that = (ClientMetrics) o;
        return Objects.equals(clientId, that.clientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId);
    }

    @Override
    public String toString() {
        return "ClientMetrics{" +
                "clientId='" + clientId + '\'' +
                ", connections=" + connections +
                ", updatesReceived=" + updatesReceived +
                ", averageLatency=" + averageLatency +
                '}';
    }
}
