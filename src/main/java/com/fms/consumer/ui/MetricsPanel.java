package com.fms.consumer.ui;

import com.fms.consumer.model.ClientMetrics;
import com.fms.consumer.model.ConsumptionMetrics;
import com.fms.consumer.service.MetricsCollector;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Dashboard panel that displays real-time consumption metrics.
 * <p>
 * Shows aggregate metrics: active connections, updates per second,
 * active vehicles, and active realms. When multi-client mode is active,
 * a per-client section is displayed with individual client metrics.
 * <p>
 * Polls the {@link MetricsCollector} every 1 second and updates the
 * display using {@link UI#access(com.vaadin.flow.server.Command)} for
 * thread-safe UI updates.
 */
public class MetricsPanel extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(MetricsPanel.class);

    private final Span activeConnectionsLabel;
    private final Span updatesPerSecondLabel;
    private final Span activeVehiclesLabel;
    private final Span activeRealmsLabel;
    private final VerticalLayout perClientSection;
    private final H4 perClientHeader;

    private MetricsCollector metricsCollector;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> refreshTask;

    /**
     * Creates a new MetricsPanel with all metric labels initialized to zero values.
     */
    public MetricsPanel() {
        setPadding(true);
        setSpacing(true);

        H4 header = new H4("Metrics");
        header.getStyle().set("margin", "0");

        activeConnectionsLabel = new Span("Active Connections: 0");
        updatesPerSecondLabel = new Span("Updates/sec: 0.0");
        activeVehiclesLabel = new Span("Active Vehicles: 0");
        activeRealmsLabel = new Span("Active Realms: 0");

        // Per-client section (hidden by default)
        perClientHeader = new H4("Per-Client Metrics");
        perClientHeader.getStyle().set("margin-top", "1em").set("margin-bottom", "0");
        perClientSection = new VerticalLayout();
        perClientSection.setPadding(false);
        perClientSection.setSpacing(true);
        perClientSection.setVisible(false);

        add(header, activeConnectionsLabel, updatesPerSecondLabel,
                activeVehiclesLabel, activeRealmsLabel, perClientHeader, perClientSection);
        perClientHeader.setVisible(false);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-panel-refresh");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Sets the MetricsCollector that this panel will poll for data.
     *
     * @param metricsCollector the metrics collector instance
     */
    public void setMetricsCollector(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    /**
     * Starts the periodic auto-refresh timer that polls the MetricsCollector
     * every 1 second and updates the UI.
     */
    public void startAutoRefresh() {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            log.debug("MetricsPanel refresh already running");
            return;
        }
        refreshTask = scheduler.scheduleAtFixedRate(this::refreshMetrics, 0, 1, TimeUnit.SECONDS);
        log.info("MetricsPanel auto-refresh started (1-second interval)");
    }

    /**
     * Starts the periodic refresh timer (alias for {@link #startAutoRefresh()}).
     */
    public void start() {
        startAutoRefresh();
    }

    /**
     * Stops the periodic auto-refresh timer.
     */
    public void stopAutoRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
            log.info("MetricsPanel auto-refresh stopped");
        }
    }

    /**
     * Stops the periodic refresh timer (alias for {@link #stopAutoRefresh()}).
     */
    public void stop() {
        stopAutoRefresh();
    }

    /**
     * Manually refreshes the metrics display by reading current data from the MetricsCollector
     * and updating all labels. Uses UI.access() for thread-safe UI updates.
     * <p>
     * This method can be called directly to trigger an immediate refresh outside the
     * auto-refresh cycle.
     */
    public void refresh() {
        refreshMetrics();
    }

    /**
     * Shuts down the scheduled executor on destruction, ensuring clean resource release.
     */
    @PreDestroy
    public void destroy() {
        stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("MetricsPanel scheduler shut down");
    }

    /**
     * Polls the MetricsCollector and updates the UI labels.
     * Uses UI.access() for thread-safe updates from the background thread.
     */
    private void refreshMetrics() {
        if (metricsCollector == null) {
            return;
        }

        try {
            ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
            Map<String, ClientMetrics> clientMetrics = metricsCollector.getPerClientMetrics();

            getUI().ifPresent(ui -> ui.access(() -> {
                activeConnectionsLabel.setText("Active Connections: " + metrics.getActiveConnections());
                updatesPerSecondLabel.setText("Updates/sec: " + String.format("%.1f", metrics.getUpdatesPerSecond()));
                activeVehiclesLabel.setText("Active Vehicles: " + metrics.getActiveVehicles());
                activeRealmsLabel.setText("Active Realms: " + metrics.getActiveRealms());

                updatePerClientSection(clientMetrics);
            }));
        } catch (Exception e) {
            log.warn("Error refreshing metrics display", e);
        }
    }

    /**
     * Updates the per-client metrics section. Shows individual client
     * metrics only when there are multiple clients (multi-client mode).
     */
    private void updatePerClientSection(Map<String, ClientMetrics> clientMetrics) {
        boolean multiClient = clientMetrics != null && clientMetrics.size() > 1;
        perClientHeader.setVisible(multiClient);
        perClientSection.setVisible(multiClient);

        if (!multiClient) {
            perClientSection.removeAll();
            return;
        }

        perClientSection.removeAll();
        clientMetrics.forEach((clientId, cm) -> {
            Span clientLine = new Span(String.format(
                    "Client %s — Connections: %d, Updates: %d, Avg Latency: %.1f ms",
                    cm.getClientId(), cm.getConnections(), cm.getUpdatesReceived(), cm.getAverageLatency()
            ));
            perClientSection.add(clientLine);
        });
    }

    // --- Package-private accessors for testing ---

    Span getActiveConnectionsLabel() {
        return activeConnectionsLabel;
    }

    Span getUpdatesPerSecondLabel() {
        return updatesPerSecondLabel;
    }

    Span getActiveVehiclesLabel() {
        return activeVehiclesLabel;
    }

    Span getActiveRealmsLabel() {
        return activeRealmsLabel;
    }

    VerticalLayout getPerClientSection() {
        return perClientSection;
    }

    H4 getPerClientHeader() {
        return perClientHeader;
    }
}
