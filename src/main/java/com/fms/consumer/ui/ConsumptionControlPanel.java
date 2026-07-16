package com.fms.consumer.ui;

import com.fms.consumer.model.ConsumptionMode;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.service.ConsumptionOrchestrator;
import com.fms.consumer.service.LocationPollingService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Control panel component for managing consumption modes.
 * <p>
 * Provides buttons for starting and stopping Random Mode and Controlled Mode,
 * and displays the current consumption mode status (IDLE/RANDOM/CONTROLLED).
 * <p>
 * Button states are managed based on the current mode: buttons for starting a mode
 * are disabled when that mode is already active, and stop buttons are disabled
 * when the respective mode is inactive.
 */
public class ConsumptionControlPanel extends VerticalLayout {

    private final Button startRandomButton;
    private final Button stopRandomButton;
    private final Button startControlledButton;
    private final Button stopControlledButton;
    private final Span modeStatusLabel;
    private final Checkbox adaptiveThrottlingCheckbox;

    private ConsumptionOrchestrator orchestrator;
    private LocationPollingService locationPollingService;
    private Supplier<Set<Vehicle>> vehicleSupplier;

    /**
     * Creates a new ConsumptionControlPanel with all controls in their initial state.
     */
    public ConsumptionControlPanel() {
        // Mode status display
        modeStatusLabel = new Span("Mode: IDLE");
        modeStatusLabel.getElement().getStyle().set("font-weight", "bold");
        modeStatusLabel.getElement().getStyle().set("font-size", "1.1em");

        // Adaptive throttling checkbox
        adaptiveThrottlingCheckbox = new Checkbox("Adaptive Throttling");
        adaptiveThrottlingCheckbox.setValue(false);
        adaptiveThrottlingCheckbox.addValueChangeListener(event -> onAdaptiveThrottlingChanged(event.getValue()));

        // Random Mode buttons
        startRandomButton = new Button("Start Random Mode", event -> onStartRandomMode());
        startRandomButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        stopRandomButton = new Button("Stop Random Mode", event -> onStopRandomMode());
        stopRandomButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        stopRandomButton.setEnabled(false);

        // Controlled Mode buttons
        startControlledButton = new Button("Start Controlled Mode", event -> onStartControlledMode());
        startControlledButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        stopControlledButton = new Button("Stop Controlled Mode", event -> onStopControlledMode());
        stopControlledButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        stopControlledButton.setEnabled(false);

        // Layout
        HorizontalLayout statusRow = new HorizontalLayout(modeStatusLabel, adaptiveThrottlingCheckbox);
        statusRow.setAlignItems(Alignment.CENTER);
        statusRow.setSpacing(true);

        HorizontalLayout randomControls = new HorizontalLayout(startRandomButton, stopRandomButton);
        randomControls.setSpacing(true);

        HorizontalLayout controlledControls = new HorizontalLayout(startControlledButton, stopControlledButton);
        controlledControls.setSpacing(true);

        setPadding(true);
        setSpacing(true);
        add(statusRow, randomControls, controlledControls);
    }

    /**
     * Sets the ConsumptionOrchestrator that this panel will control.
     *
     * @param orchestrator the orchestrator instance
     */
    public void setOrchestrator(ConsumptionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
        refreshStatus();
    }

    /**
     * Sets the supplier that provides the currently selected vehicles
     * from the RealmVehicleTree for Controlled Mode.
     *
     * @param vehicleSupplier a supplier returning the set of selected vehicles
     */
    public void setVehicleSupplier(Supplier<Set<Vehicle>> vehicleSupplier) {
        this.vehicleSupplier = vehicleSupplier;
    }

    /**
     * Sets the LocationPollingService for adaptive throttling control.
     *
     * @param locationPollingService the location polling service instance
     */
    public void setLocationPollingService(LocationPollingService locationPollingService) {
        this.locationPollingService = locationPollingService;
    }

    /**
     * Refreshes the button enabled/disabled states and status label
     * based on the current mode from the orchestrator.
     */
    public void refreshStatus() {
        ConsumptionMode mode = getMode();
        updateModeLabel(mode);
        updateButtonStates(mode);
    }

    // --- Event handlers ---

    private void onStartRandomMode() {
        if (orchestrator == null) {
            return;
        }
        orchestrator.startRandomMode();
        refreshStatus();
    }

    private void onStopRandomMode() {
        if (orchestrator == null) {
            return;
        }
        orchestrator.stopRandomMode();
        refreshStatus();
    }

    private void onStartControlledMode() {
        if (orchestrator == null) {
            return;
        }
        Set<Vehicle> selectedVehicles = getSelectedVehicles();
        if (selectedVehicles.isEmpty()) {
            try {
                Notification notification = Notification.show(
                    "Please select vehicles in the tree first", 4000, Notification.Position.MIDDLE);
                notification.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
            } catch (Exception e) {
                // No UI context
            }
            return;
        }
        orchestrator.startControlledMode(selectedVehicles);
        refreshStatus();
    }

    private void onStopControlledMode() {
        if (orchestrator == null) {
            return;
        }
        orchestrator.stopAllControlledSessions();
        refreshStatus();
    }

    private void onAdaptiveThrottlingChanged(boolean enabled) {
        if (locationPollingService != null) {
            locationPollingService.setAdaptiveThrottlingEnabled(enabled);
        }
    }

    // --- Internal helpers ---

    private ConsumptionMode getMode() {
        if (orchestrator == null) {
            return ConsumptionMode.IDLE;
        }
        return orchestrator.getCurrentMode();
    }

    private Set<Vehicle> getSelectedVehicles() {
        if (vehicleSupplier == null) {
            return Collections.emptySet();
        }
        Set<Vehicle> vehicles = vehicleSupplier.get();
        return vehicles != null ? vehicles : Collections.emptySet();
    }

    private void updateModeLabel(ConsumptionMode mode) {
        modeStatusLabel.setText("Mode: " + mode.name());
    }

    private void updateButtonStates(ConsumptionMode mode) {
        switch (mode) {
            case IDLE:
                startRandomButton.setEnabled(true);
                stopRandomButton.setEnabled(false);
                startControlledButton.setEnabled(true);
                stopControlledButton.setEnabled(false);
                break;
            case RANDOM:
                startRandomButton.setEnabled(false);
                stopRandomButton.setEnabled(true);
                startControlledButton.setEnabled(false);
                stopControlledButton.setEnabled(false);
                break;
            case CONTROLLED:
                startRandomButton.setEnabled(false);
                stopRandomButton.setEnabled(false);
                startControlledButton.setEnabled(false);
                stopControlledButton.setEnabled(true);
                break;
        }
    }

    // --- Package-private accessors for testing ---

    Button getStartRandomButton() {
        return startRandomButton;
    }

    Button getStopRandomButton() {
        return stopRandomButton;
    }

    Button getStartControlledButton() {
        return startControlledButton;
    }

    Button getStopControlledButton() {
        return stopControlledButton;
    }

    Span getModeStatusLabel() {
        return modeStatusLabel;
    }

    Checkbox getAdaptiveThrottlingCheckbox() {
        return adaptiveThrottlingCheckbox;
    }
}
