package com.fms.consumer.ui;

import com.fms.consumer.service.ConsumptionOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the MultiClientConfigPanel component.
 * Tests input validation, button interaction, status display,
 * and integration with ConsumptionOrchestrator.configureMultiClient.
 */
class MultiClientConfigPanelTest {

    private MultiClientConfigPanel panel;
    private ConsumptionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        panel = new MultiClientConfigPanel();
        orchestrator = mock(ConsumptionOrchestrator.class);
    }

    @Test
    void constructor_initialValues() {
        assertEquals(1, panel.getClientCountField().getValue());
        assertEquals("Current clients: 1", panel.getStatusLabel().getText());
        assertTrue(panel.getConfigureButton().isEnabled());
    }

    @Test
    void constructor_fieldMinMax() {
        assertEquals(1, panel.getClientCountField().getMin());
        assertEquals(100, panel.getClientCountField().getMax());
    }

    @Test
    void setOrchestrator_refreshesStatus() {
        when(orchestrator.getClientCount()).thenReturn(5);

        panel.setOrchestrator(orchestrator);

        assertEquals("Current clients: 5", panel.getStatusLabel().getText());
        assertEquals(5, panel.getClientCountField().getValue());
    }

    @Test
    void configureButton_validValue_callsOrchestrator() {
        when(orchestrator.getClientCount()).thenReturn(1);
        panel.setOrchestrator(orchestrator);

        panel.getClientCountField().setValue(10);
        panel.getConfigureButton().click();

        verify(orchestrator).configureMultiClient(10);
    }

    @Test
    void configureButton_validValue_updatesStatusLabel() {
        when(orchestrator.getClientCount()).thenReturn(1);
        panel.setOrchestrator(orchestrator);

        panel.getClientCountField().setValue(25);
        panel.getConfigureButton().click();

        assertEquals("Current clients: 25", panel.getStatusLabel().getText());
    }

    @Test
    void configureButton_nullValue_doesNotCallOrchestrator() {
        when(orchestrator.getClientCount()).thenReturn(1);
        panel.setOrchestrator(orchestrator);

        panel.getClientCountField().setValue(null);
        panel.getConfigureButton().click();

        verify(orchestrator, never()).configureMultiClient(anyInt());
    }

    @Test
    void configureButton_valueBelowMin_doesNotCallOrchestrator() {
        when(orchestrator.getClientCount()).thenReturn(1);
        panel.setOrchestrator(orchestrator);

        panel.getClientCountField().setValue(0);
        panel.getConfigureButton().click();

        verify(orchestrator, never()).configureMultiClient(anyInt());
    }

    @Test
    void configureButton_valueAboveMax_doesNotCallOrchestrator() {
        when(orchestrator.getClientCount()).thenReturn(1);
        panel.setOrchestrator(orchestrator);

        panel.getClientCountField().setValue(101);
        panel.getConfigureButton().click();

        verify(orchestrator, never()).configureMultiClient(anyInt());
    }

    @Test
    void configureButton_orchestratorThrowsIllegalArgument_handlesGracefully() {
        when(orchestrator.getClientCount()).thenReturn(1);
        panel.setOrchestrator(orchestrator);
        doThrow(new IllegalArgumentException("Client count must not exceed 50"))
                .when(orchestrator).configureMultiClient(60);

        panel.getClientCountField().setValue(60);

        // Should not throw - error is shown in status label instead
        assertDoesNotThrow(() -> panel.getConfigureButton().click());
        assertTrue(panel.getStatusLabel().getText().contains("Client count must not exceed 50"));
    }

    @Test
    void configureButton_withNullOrchestrator_doesNothing() {
        // No orchestrator set
        panel.getClientCountField().setValue(10);
        assertDoesNotThrow(() -> panel.getConfigureButton().click());
    }

    @Test
    void configureButton_minimumValue_accepted() {
        when(orchestrator.getClientCount()).thenReturn(1);
        panel.setOrchestrator(orchestrator);

        panel.getClientCountField().setValue(1);
        panel.getConfigureButton().click();

        verify(orchestrator).configureMultiClient(1);
        assertEquals("Current clients: 1", panel.getStatusLabel().getText());
    }

    @Test
    void configureButton_maximumValue_accepted() {
        when(orchestrator.getClientCount()).thenReturn(1);
        panel.setOrchestrator(orchestrator);

        panel.getClientCountField().setValue(100);
        panel.getConfigureButton().click();

        verify(orchestrator).configureMultiClient(100);
        assertEquals("Current clients: 100", panel.getStatusLabel().getText());
    }

    @Test
    void refreshStatus_updatesFromOrchestrator() {
        when(orchestrator.getClientCount()).thenReturn(3);
        panel.setOrchestrator(orchestrator);

        when(orchestrator.getClientCount()).thenReturn(7);
        panel.refreshStatus();

        assertEquals("Current clients: 7", panel.getStatusLabel().getText());
        assertEquals(7, panel.getClientCountField().getValue());
    }

    @Test
    void refreshStatus_withNullOrchestrator_keepsCurrent() {
        // No orchestrator set, status stays at initial
        panel.refreshStatus();
        assertEquals("Current clients: 1", panel.getStatusLabel().getText());
    }
}
