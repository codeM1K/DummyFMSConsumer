package com.fms.consumer.ui;

import com.fms.consumer.model.ConsumptionMode;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.model.VehicleStatus;
import com.fms.consumer.service.ConsumptionOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the ConsumptionControlPanel component.
 * Tests button states, mode status display, and interaction with
 * ConsumptionOrchestrator and vehicle supplier.
 */
class ConsumptionControlPanelTest {

    private ConsumptionControlPanel panel;
    private ConsumptionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        panel = new ConsumptionControlPanel();
        orchestrator = mock(ConsumptionOrchestrator.class);
    }

    @Test
    void constructor_initialButtonStates() {
        // Initially in IDLE mode (no orchestrator set)
        assertTrue(panel.getStartRandomButton().isEnabled());
        assertFalse(panel.getStopRandomButton().isEnabled());
        assertTrue(panel.getStartControlledButton().isEnabled());
        assertFalse(panel.getStopControlledButton().isEnabled());
    }

    @Test
    void constructor_initialModeLabel() {
        assertEquals("Mode: IDLE", panel.getModeStatusLabel().getText());
    }

    @Test
    void setOrchestrator_updatesStatusFromOrchestrator() {
        when(orchestrator.getCurrentMode()).thenReturn(ConsumptionMode.RANDOM);

        panel.setOrchestrator(orchestrator);

        assertEquals("Mode: RANDOM", panel.getModeStatusLabel().getText());
        assertFalse(panel.getStartRandomButton().isEnabled());
        assertTrue(panel.getStopRandomButton().isEnabled());
        assertFalse(panel.getStartControlledButton().isEnabled());
        assertFalse(panel.getStopControlledButton().isEnabled());
    }

    @Test
    void refreshStatus_idleMode_allStartEnabled_allStopDisabled() {
        when(orchestrator.getCurrentMode()).thenReturn(ConsumptionMode.IDLE);
        panel.setOrchestrator(orchestrator);

        panel.refreshStatus();

        assertEquals("Mode: IDLE", panel.getModeStatusLabel().getText());
        assertTrue(panel.getStartRandomButton().isEnabled());
        assertFalse(panel.getStopRandomButton().isEnabled());
        assertTrue(panel.getStartControlledButton().isEnabled());
        assertFalse(panel.getStopControlledButton().isEnabled());
    }

    @Test
    void refreshStatus_randomMode_onlyStopRandomEnabled() {
        when(orchestrator.getCurrentMode()).thenReturn(ConsumptionMode.RANDOM);
        panel.setOrchestrator(orchestrator);

        panel.refreshStatus();

        assertEquals("Mode: RANDOM", panel.getModeStatusLabel().getText());
        assertFalse(panel.getStartRandomButton().isEnabled());
        assertTrue(panel.getStopRandomButton().isEnabled());
        assertFalse(panel.getStartControlledButton().isEnabled());
        assertFalse(panel.getStopControlledButton().isEnabled());
    }

    @Test
    void refreshStatus_controlledMode_onlyStopControlledEnabled() {
        when(orchestrator.getCurrentMode()).thenReturn(ConsumptionMode.CONTROLLED);
        panel.setOrchestrator(orchestrator);

        panel.refreshStatus();

        assertEquals("Mode: CONTROLLED", panel.getModeStatusLabel().getText());
        assertFalse(panel.getStartRandomButton().isEnabled());
        assertFalse(panel.getStopRandomButton().isEnabled());
        assertFalse(panel.getStartControlledButton().isEnabled());
        assertTrue(panel.getStopControlledButton().isEnabled());
    }

    @Test
    void setVehicleSupplier_usedByControlledMode() {
        Set<Vehicle> vehicles = new HashSet<>();
        vehicles.add(new Vehicle("v1", "Truck 1", "r1", VehicleStatus.ACTIVE));
        Supplier<Set<Vehicle>> supplier = () -> vehicles;

        when(orchestrator.getCurrentMode()).thenReturn(ConsumptionMode.IDLE);
        panel.setOrchestrator(orchestrator);
        panel.setVehicleSupplier(supplier);

        // Trigger start controlled mode
        when(orchestrator.getCurrentMode()).thenReturn(ConsumptionMode.CONTROLLED);
        panel.getStartControlledButton().click();

        verify(orchestrator).startControlledMode(vehicles);
    }

    @Test
    void startRandomButton_callsOrchestratorStartRandom() {
        when(orchestrator.getCurrentMode()).thenReturn(ConsumptionMode.IDLE)
                .thenReturn(ConsumptionMode.RANDOM);
        panel.setOrchestrator(orchestrator);

        panel.getStartRandomButton().click();

        verify(orchestrator).startRandomMode();
    }

    @Test
    void stopRandomButton_callsOrchestratorStopRandom() {
        when(orchestrator.getCurrentMode()).thenReturn(ConsumptionMode.RANDOM)
                .thenReturn(ConsumptionMode.IDLE);
        panel.setOrchestrator(orchestrator);

        panel.getStopRandomButton().click();

        verify(orchestrator).stopRandomMode();
    }

    @Test
    void startControlledButton_withNoSupplier_callsWithEmptySet() {
        when(orchestrator.getCurrentMode()).thenReturn(ConsumptionMode.IDLE)
                .thenReturn(ConsumptionMode.CONTROLLED);
        panel.setOrchestrator(orchestrator);

        panel.getStartControlledButton().click();

        verify(orchestrator).startControlledMode(Collections.emptySet());
    }

    @Test
    void stopControlledButton_callsOrchestratorStopControlled() {
        Set<Vehicle> vehicles = new HashSet<>();
        vehicles.add(new Vehicle("v1", "Truck 1", "r1", VehicleStatus.ACTIVE));
        panel.setVehicleSupplier(() -> vehicles);

        when(orchestrator.getCurrentMode()).thenReturn(ConsumptionMode.CONTROLLED)
                .thenReturn(ConsumptionMode.IDLE);
        panel.setOrchestrator(orchestrator);

        panel.getStopControlledButton().click();

        verify(orchestrator).stopControlledMode(vehicles);
    }

    @Test
    void startRandomButton_withNullOrchestrator_doesNothing() {
        // No orchestrator set — clicking should not throw
        assertDoesNotThrow(() -> panel.getStartRandomButton().click());
    }

    @Test
    void stopRandomButton_withNullOrchestrator_doesNothing() {
        assertDoesNotThrow(() -> panel.getStopRandomButton().click());
    }

    @Test
    void startControlledButton_withNullOrchestrator_doesNothing() {
        assertDoesNotThrow(() -> panel.getStartControlledButton().click());
    }

    @Test
    void stopControlledButton_withNullOrchestrator_doesNothing() {
        assertDoesNotThrow(() -> panel.getStopControlledButton().click());
    }

    @Test
    void vehicleSupplier_returningNull_treatedAsEmptySet() {
        when(orchestrator.getCurrentMode()).thenReturn(ConsumptionMode.IDLE)
                .thenReturn(ConsumptionMode.CONTROLLED);
        panel.setOrchestrator(orchestrator);
        panel.setVehicleSupplier(() -> null);

        panel.getStartControlledButton().click();

        verify(orchestrator).startControlledMode(Collections.emptySet());
    }

    @Test
    void refreshStatus_withNullOrchestrator_showsIdle() {
        panel.refreshStatus();

        assertEquals("Mode: IDLE", panel.getModeStatusLabel().getText());
        assertTrue(panel.getStartRandomButton().isEnabled());
        assertFalse(panel.getStopRandomButton().isEnabled());
    }
}
