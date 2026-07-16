package com.fms.consumer.ui;

import com.fms.consumer.model.ClientMetrics;
import com.fms.consumer.model.ConsumptionMetrics;
import com.fms.consumer.service.LocationPollingService;
import com.fms.consumer.service.MetricsCollector;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Dashboard panel that displays real-time consumption metrics.
 * <p>
 * Shows aggregate metrics (active connections, updates/sec, active vehicles, active realms)
 * in a prominent row, plus a polling status row showing poll interval, response time,
 * mode (RANDOM/FIXED), and clients/cycle when in random mode.
 * <p>
 * When multi-client mode is active, a per-client section is displayed with individual
 * client metrics.
 * <p>
 * Polls the {@link MetricsCollector} every 1 second and updates the display using
 * {@link UI#access(com.vaadin.flow.server.Command)} for thread-safe UI updates.
 */
public class MetricsPanel extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(MetricsPanel.class);

    // Main metrics labels
    private final Span activeConnectionsLabel;
    private final Span updatesPerSecondLabel;
    private final Span activeVehiclesLabel;
    private final Span activeRealmsLabel;
    private final Span elapsedTimeLabel;

    // Timer state
    private volatile Instant testStartTime = null;

    // Polling status labels
    private final Span pollIntervalLabel;
    private final Span responseTimeLabel;
    private final Span pollModeLabel;
    private final Span randomClientCountLabel;

    // Per-client section
    private final H4 perClientHeader;
    private final VerticalLayout perClientSection;

    private MetricsCollector metricsCollector;
    private LocationPollingService locationPollingService;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> refreshTask;

    /**
     * Creates a new MetricsPanel with all metric labels initialized to zero/default values.
     */
    public MetricsPanel() {
        setPadding(true);
        setSpacing(true);

        H4 header = new H4("Live Metrics");
        header.getStyle().set("margin", "0");

        // Main metrics - bold and prominent
        activeConnectionsLabel = createMetricLabel("Active Connections", "0");
        updatesPerSecondLabel = createMetricLabel("Updates/sec", "0.0");
        activeVehiclesLabel = createMetricLabel("Active Vehicles", "0");
        activeRealmsLabel = createMetricLabel("Active Realms", "0");
        elapsedTimeLabel = createMetricLabel("Elapsed", "--:--");

        HorizontalLayout mainMetrics = new HorizontalLayout(
                activeConnectionsLabel, updatesPerSecondLabel, activeVehiclesLabel, activeRealmsLabel, elapsedTimeLabel);
        mainMetrics.setSpacing(true);
        mainMetrics.setWidthFull();

        // Polling status row
        pollIntervalLabel = new Span("Poll Interval: 2s");
        responseTimeLabel = new Span("Response Time: -");
        pollModeLabel = new Span("Mode: FIXED");
        randomClientCountLabel = new Span("Clients/cycle: -");
        randomClientCountLabel.setVisible(false);

        HorizontalLayout pollingStatus = new HorizontalLayout(
                pollIntervalLabel, responseTimeLabel, pollModeLabel, randomClientCountLabel);
        pollingStatus.setSpacing(true);
        pollingStatus.setWidthFull();
        pollingStatus.getStyle().set("font-size", "0.9em").set("color", "#666");

        // Per-client section (hidden by default)
        perClientHeader = new H4("Per-Client Metrics");
        perClientHeader.getStyle().set("margin-top", "0.5em").set("margin-bottom", "0");
        perClientSection = new VerticalLayout();
        perClientSection.setPadding(false);
        perClientSection.setSpacing(false);
        perClientSection.setVisible(false);
        perClientHeader.setVisible(false);

        add(header, mainMetrics, pollingStatus, perClientHeader, perClientSection);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-panel-refresh");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Creates a styled metric label with bold text and background styling.
     */
    private Span createMetricLabel(String name, String initialValue) {
        Span span = new Span(name + ": " + initialValue);
        span.getStyle().set("font-weight", "bold").set("padding", "4px 8px")
                .set("background", "#f5f5f5").set("border-radius", "4px");
        return span;
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
     * Sets the LocationPollingService for displaying polling status information.
     *
     * @param locationPollingService the location polling service instance
     */
    public void setLocationPollingService(LocationPollingService locationPollingService) {
        this.locationPollingService = locationPollingService;
    }

    /**
     * Starts the elapsed time counter from the current instant.
     */
    public void startTimer() {
        this.testStartTime = Instant.now();
    }

    /**
     * Stops the elapsed time counter and resets the display to --:--.
     */
    public void stopTimer() {
        this.testStartTime = null;
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

                // Update elapsed time
                if (testStartTime != null) {
                    Duration elapsed = Duration.between(testStartTime, Instant.now());
                    long hours = elapsed.toHours();
                    long minutes = elapsed.toMinutesPart();
                    long seconds = elapsed.toSecondsPart();
                    if (hours > 0) {
                        elapsedTimeLabel.setText(String.format("Elapsed: %d:%02d:%02d", hours, minutes, seconds));
                    } else {
                        elapsedTimeLabel.setText(String.format("Elapsed: %02d:%02d", minutes, seconds));
                    }
                } else {
                    elapsedTimeLabel.setText("Elapsed: --:--");
                }

                // Update polling status
                if (locationPollingService != null) {
                    pollIntervalLabel.setText("Poll Interval: " + locationPollingService.getCurrentPollIntervalSeconds() + "s");
                    responseTimeLabel.setText("Response Time: " + locationPollingService.getLastResponseTimeMs() + "ms");
                    pollModeLabel.setText("Mode: " + (locationPollingService.isRandomMode() ? "RANDOM" : "FIXED"));
                    if (locationPollingService.isRandomMode()) {
                        randomClientCountLabel.setText("Clients/cycle: " + locationPollingService.getCurrentRandomClientCount());
                        randomClientCountLabel.setVisible(true);
                    } else {
                        randomClientCountLabel.setVisible(false);
                    }
                }

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

    Span getPollIntervalLabel() {
        return pollIntervalLabel;
    }

    Span getResponseTimeLabel() {
        return responseTimeLabel;
    }

    Span getPollModeLabel() {
        return pollModeLabel;
    }

    Span getRandomClientCountLabel() {
        return randomClientCountLabel;
    }

    VerticalLayout getPerClientSection() {
        return perClientSection;
    }

    H4 getPerClientHeader() {
        return perClientHeader;
    }

    Span getElapsedTimeLabel() {
        return elapsedTimeLabel;
    }
}
