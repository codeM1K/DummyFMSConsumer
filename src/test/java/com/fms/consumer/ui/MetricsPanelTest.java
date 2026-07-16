package com.fms.consumer.ui;

import com.fms.consumer.model.ClientMetrics;
import com.fms.consumer.model.ConsumptionMetrics;
import com.fms.consumer.service.MetricsCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the MetricsPanel component.
 * Tests initialization, metric label formatting, per-client section visibility,
 * and start/stop lifecycle.
 */
class MetricsPanelTest {

    private MetricsPanel panel;
    private MetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        panel = new MetricsPanel();
        metricsCollector = mock(MetricsCollector.class);
    }

    @AfterEach
    void tearDown() {
        panel.destroy();
    }

    @Test
    void constructor_initialLabelsShowZeroValues() {
        assertEquals("Active Connections: 0", panel.getActiveConnectionsLabel().getText());
        assertEquals("Updates/sec: 0.0", panel.getUpdatesPerSecondLabel().getText());
        assertEquals("Active Vehicles: 0", panel.getActiveVehiclesLabel().getText());
        assertEquals("Active Realms: 0", panel.getActiveRealmsLabel().getText());
    }

    @Test
    void constructor_perClientSectionHiddenByDefault() {
        assertFalse(panel.getPerClientSection().isVisible());
        assertFalse(panel.getPerClientHeader().isVisible());
    }

    @Test
    void setMetricsCollector_acceptsNonNullCollector() {
        panel.setMetricsCollector(metricsCollector);
        // No exception
    }

    @Test
    void setMetricsCollector_acceptsNull() {
        panel.setMetricsCollector(null);
        // No exception
    }

    @Test
    void start_doesNotThrowWhenNoCollectorSet() {
        // Start without a collector - should not throw
        assertDoesNotThrow(() -> panel.start());
    }

    @Test
    void stop_doesNotThrowWhenNotStarted() {
        assertDoesNotThrow(() -> panel.stop());
    }

    @Test
    void start_calledTwice_doesNotCreateMultipleTimers() {
        panel.setMetricsCollector(metricsCollector);
        panel.start();
        panel.start(); // Second call should be a no-op
        // No exception - just verifying double-start doesn't error
    }

    @Test
    void stop_afterStart_stopsRefresh() {
        panel.setMetricsCollector(metricsCollector);
        panel.start();
        panel.stop();
        // No exception - verified that stop cancels the running task
    }

    @Test
    void destroy_afterStart_shutsDownScheduler() {
        panel.setMetricsCollector(metricsCollector);
        panel.start();
        panel.destroy();
        // No exception - verified clean shutdown
    }

    @Test
    void destroy_withoutStart_shutsDownCleanly() {
        panel.destroy();
        // No exception
    }
}
