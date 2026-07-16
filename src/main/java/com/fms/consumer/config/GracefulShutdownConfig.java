package com.fms.consumer.config;

import com.fms.consumer.integration.WebSocketClientPool;
import com.fms.consumer.service.ConsumptionOrchestrator;
import com.fms.consumer.service.DiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Handles graceful shutdown of the application.
 * When a SIGTERM or application shutdown event is received, ensures all
 * active WebSocket connections are closed and scheduled tasks are stopped.
 * Works with Spring Boot's server.shutdown=graceful setting for coordinated shutdown.
 */
@Component
public class GracefulShutdownConfig {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownConfig.class);

    private final WebSocketClientPool webSocketClientPool;
    private final DiscoveryService discoveryService;

    public GracefulShutdownConfig(WebSocketClientPool webSocketClientPool,
                                  DiscoveryService discoveryService) {
        this.webSocketClientPool = webSocketClientPool;
        this.discoveryService = discoveryService;
    }

    @EventListener(ContextClosedEvent.class)
    public void onApplicationShutdown() {
        log.info("Application shutdown detected. Closing all WebSocket connections and stopping periodic tasks...");

        try {
            discoveryService.stopPeriodicRefresh();
            log.info("Discovery periodic refresh stopped");
        } catch (Exception e) {
            log.warn("Error stopping discovery refresh during shutdown: {}", e.getMessage());
        }

        try {
            webSocketClientPool.closeAllConnections();
            log.info("All WebSocket connections closed");
        } catch (Exception e) {
            log.warn("Error closing WebSocket connections during shutdown: {}", e.getMessage());
        }

        log.info("Graceful shutdown complete");
    }
}
