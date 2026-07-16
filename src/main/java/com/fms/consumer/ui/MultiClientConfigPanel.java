package com.fms.consumer.ui;

import com.fms.consumer.service.ConsumptionOrchestrator;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;

/**
 * Configuration panel for multi-client simulation settings.
 * <p>
 * Allows users to specify the number of simulated clients (1–100) and apply the
 * configuration via the ConsumptionOrchestrator. Displays the current client count
 * and shows error notifications on invalid input.
 * <p>
 * Requirements: 7.1 (specify number of simulated clients), 10.6 (multi-client config panel).
 */
public class MultiClientConfigPanel extends VerticalLayout {

    private static final int MIN_CLIENTS = 1;
    private static final int MAX_CLIENTS = 100;

    private final IntegerField clientCountField;
    private final Button configureButton;
    private final Span statusLabel;

    private ConsumptionOrchestrator orchestrator;

    /**
     * Creates a new MultiClientConfigPanel with default settings.
     * The initial client count value is 1.
     */
    public MultiClientConfigPanel() {
        // Client count input field with validation
        clientCountField = new IntegerField("Number of Clients");
        clientCountField.setMin(MIN_CLIENTS);
        clientCountField.setMax(MAX_CLIENTS);
        clientCountField.setValue(1);
        clientCountField.setStepButtonsVisible(true);
        clientCountField.setHelperText("Min: " + MIN_CLIENTS + ", Max: " + MAX_CLIENTS);

        // Configure button
        configureButton = new Button("Configure", event -> onConfigure());
        configureButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // Status label showing current configuration
        statusLabel = new Span("Current clients: 1");
        statusLabel.getElement().getStyle().set("font-weight", "bold");

        // Layout arrangement
        HorizontalLayout inputRow = new HorizontalLayout(clientCountField, configureButton);
        inputRow.setAlignItems(Alignment.BASELINE);
        inputRow.setSpacing(true);

        setPadding(true);
        setSpacing(true);
        add(statusLabel, inputRow);
    }

    /**
     * Sets the ConsumptionOrchestrator that this panel will configure.
     *
     * @param orchestrator the orchestrator instance
     */
    public void setOrchestrator(ConsumptionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
        refreshStatus();
    }

    /**
     * Refreshes the status label to reflect the current client count
     * from the orchestrator.
     */
    public void refreshStatus() {
        if (orchestrator != null) {
            int currentCount = orchestrator.getClientCount();
            statusLabel.setText("Current clients: " + currentCount);
            clientCountField.setValue(currentCount);
        }
    }

    // --- Event handler ---

    private void onConfigure() {
        if (orchestrator == null) {
            return;
        }

        Integer value = clientCountField.getValue();

        // Validate input
        if (value == null) {
            showError("Please enter a valid number of clients.");
            return;
        }

        if (value < MIN_CLIENTS || value > MAX_CLIENTS) {
            showError("Client count must be between " + MIN_CLIENTS + " and " + MAX_CLIENTS + ".");
            return;
        }

        try {
            orchestrator.configureMultiClient(value);
            statusLabel.setText("Current clients: " + value);
            showSuccess("Multi-client configuration updated to " + value + " client(s).");
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private void showError(String message) {
        statusLabel.setText("Error: " + message);
        try {
            Notification notification = Notification.show(message, 4000, Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        } catch (IllegalStateException e) {
            // No UI available (e.g., during tests) - status label already updated
        }
    }

    private void showSuccess(String message) {
        try {
            Notification.show(message, 3000, Notification.Position.BOTTOM_START);
        } catch (IllegalStateException e) {
            // No UI available (e.g., during tests) - status label already updated
        }
    }

    // --- Package-private accessors for testing ---

    IntegerField getClientCountField() {
        return clientCountField;
    }

    Button getConfigureButton() {
        return configureButton;
    }

    Span getStatusLabel() {
        return statusLabel;
    }
}
