package com.fms.consumer.ui;

import com.fms.consumer.model.ConsumptionMode;
import com.fms.consumer.service.ConsumptionOrchestrator;
import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for consumption status display in the {@link ConsumptionControlPanel} component.
 *
 * <p><b>Property 14: Consumption Status Display</b></p>
 * <p>"For any set of selected vehicles, the UI SHALL display the current consumption status
 * (active/inactive) for each vehicle."</p>
 *
 * <p><b>Validates: Requirements 5.7</b></p>
 */
class ConsumptionStatusDisplayPropertyTest {

    /**
     * For any consumption mode returned by the orchestrator, the ConsumptionControlPanel
     * SHALL display the correct mode status text matching the mode name.
     *
     * <p><b>Validates: Requirements 5.7</b></p>
     */
    @Property(tries = 50)
    void panelDisplaysCorrectModeStatus(@ForAll("consumptionModes") ConsumptionMode mode) {
        ConsumptionControlPanel panel = new ConsumptionControlPanel();
        ConsumptionOrchestrator orchestrator = mock(ConsumptionOrchestrator.class);
        when(orchestrator.getCurrentMode()).thenReturn(mode);

        panel.setOrchestrator(orchestrator);

        assertEquals("Mode: " + mode.name(), panel.getModeStatusLabel().getText(),
                "Panel must display 'Mode: " + mode.name() + "' when orchestrator reports " + mode);
    }

    /**
     * For any transition from one consumption mode to another, the ConsumptionControlPanel
     * SHALL update the displayed mode status to reflect the new mode after refreshStatus().
     *
     * <p><b>Validates: Requirements 5.7</b></p>
     */
    @Property(tries = 50)
    void panelUpdatesStatusOnModeTransition(
            @ForAll("consumptionModes") ConsumptionMode initialMode,
            @ForAll("consumptionModes") ConsumptionMode newMode) {

        ConsumptionControlPanel panel = new ConsumptionControlPanel();
        ConsumptionOrchestrator orchestrator = mock(ConsumptionOrchestrator.class);

        // Set initial mode
        when(orchestrator.getCurrentMode()).thenReturn(initialMode);
        panel.setOrchestrator(orchestrator);
        assertEquals("Mode: " + initialMode.name(), panel.getModeStatusLabel().getText());

        // Transition to new mode
        when(orchestrator.getCurrentMode()).thenReturn(newMode);
        panel.refreshStatus();
        assertEquals("Mode: " + newMode.name(), panel.getModeStatusLabel().getText(),
                "Panel must update to 'Mode: " + newMode.name() + "' after refresh");
    }

    /**
     * For any consumption mode, the button enabled states SHALL correctly indicate
     * which actions are available (active modes disable start buttons, idle enables them).
     *
     * <p><b>Validates: Requirements 5.7</b></p>
     */
    @Property(tries = 50)
    void buttonStatesReflectConsumptionStatus(@ForAll("consumptionModes") ConsumptionMode mode) {
        ConsumptionControlPanel panel = new ConsumptionControlPanel();
        ConsumptionOrchestrator orchestrator = mock(ConsumptionOrchestrator.class);
        when(orchestrator.getCurrentMode()).thenReturn(mode);

        panel.setOrchestrator(orchestrator);

        switch (mode) {
            case IDLE:
                assertTrue(panel.getStartRandomButton().isEnabled(),
                        "Start Random should be enabled in IDLE mode");
                assertFalse(panel.getStopRandomButton().isEnabled(),
                        "Stop Random should be disabled in IDLE mode");
                assertTrue(panel.getStartControlledButton().isEnabled(),
                        "Start Controlled should be enabled in IDLE mode");
                assertFalse(panel.getStopControlledButton().isEnabled(),
                        "Stop Controlled should be disabled in IDLE mode");
                break;
            case RANDOM:
                assertFalse(panel.getStartRandomButton().isEnabled(),
                        "Start Random should be disabled in RANDOM mode");
                assertTrue(panel.getStopRandomButton().isEnabled(),
                        "Stop Random should be enabled in RANDOM mode");
                assertFalse(panel.getStartControlledButton().isEnabled(),
                        "Start Controlled should be disabled in RANDOM mode");
                assertFalse(panel.getStopControlledButton().isEnabled(),
                        "Stop Controlled should be disabled in RANDOM mode");
                break;
            case CONTROLLED:
                assertFalse(panel.getStartRandomButton().isEnabled(),
                        "Start Random should be disabled in CONTROLLED mode");
                assertFalse(panel.getStopRandomButton().isEnabled(),
                        "Stop Random should be disabled in CONTROLLED mode");
                assertFalse(panel.getStartControlledButton().isEnabled(),
                        "Start Controlled should be disabled in CONTROLLED mode");
                assertTrue(panel.getStopControlledButton().isEnabled(),
                        "Stop Controlled should be enabled in CONTROLLED mode");
                break;
        }
    }

    // --- Providers ---

    @Provide
    Arbitrary<ConsumptionMode> consumptionModes() {
        return Arbitraries.of(ConsumptionMode.values());
    }
}
