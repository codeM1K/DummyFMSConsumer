package com.fms.consumer.config;

import com.fms.consumer.service.MetricsCollector;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for WebSocket connection status.
 * Reports UP when the application is running and provides
 * details about active connections.
 */
@Component
public class WebSocketHealthIndicator implements HealthIndicator {

    private final MetricsCollector metricsCollector;

    public WebSocketHealthIndicator(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    @Override
    public Health health() {
        int activeConnections = metricsCollector.getActiveConnections();
        return Health.up()
                .withDetail("activeConnections", activeConnections)
                .withDetail("totalUpdatesReceived", metricsCollector.getTotalUpdatesReceived())
                .withDetail("updatesPerSecond", metricsCollector.getUpdatesPerSecond())
                .build();
    }
}
