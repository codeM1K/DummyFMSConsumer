package com.fms.consumer.service;

import com.fms.consumer.model.ClientMetrics;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for {@link MetricsCollector} per-client metrics tracking.
 *
 * <p><b>Validates: Requirements 9.5</b></p>
 *
 * <p>Property 31: Per-Client Metrics Display —
 * "For any multi-client configuration with active consumption, the metrics panel
 * SHALL display metrics separately for each simulated client."</p>
 */
class MetricsPerClientPropertyTest {

    private MetricsCollector metricsCollector;

    @BeforeTry
    void setUp() {
        metricsCollector = new MetricsCollector();
    }

    /**
     * Property 31: For any number of distinct clients each receiving arbitrary updates,
     * getPerClientMetrics() shall return a separate entry for each client with the correct
     * update count.
     *
     * <p><b>Validates: Requirements 9.5</b></p>
     */
    @Property(tries = 500)
    void eachClient_hasIndependentMetrics(
            @ForAll("clientUpdateDistributions") List<ClientUpdateSpec> specs) {

        // Record updates for each client
        for (ClientUpdateSpec spec : specs) {
            for (int i = 0; i < spec.updateCount(); i++) {
                metricsCollector.recordLocationUpdate("vehicle-" + i, spec.clientId());
            }
        }

        Map<String, ClientMetrics> perClientMetrics = metricsCollector.getPerClientMetrics();

        // Verify each client has a separate entry
        for (ClientUpdateSpec spec : specs) {
            assertTrue(perClientMetrics.containsKey(spec.clientId()),
                    "Per-client metrics should contain entry for client: " + spec.clientId());

            ClientMetrics clientMetrics = perClientMetrics.get(spec.clientId());
            assertEquals(spec.clientId(), clientMetrics.getClientId(),
                    "Client metrics should have matching clientId");
            assertEquals(spec.updateCount(), clientMetrics.getUpdatesReceived(),
                    "Client '" + spec.clientId() + "' should have exactly " + spec.updateCount()
                            + " updates recorded, but had " + clientMetrics.getUpdatesReceived());
        }
    }

    /**
     * Property 31: For any multi-client configuration, the number of entries in
     * getPerClientMetrics() shall equal the number of distinct clients that received updates.
     *
     * <p><b>Validates: Requirements 9.5</b></p>
     */
    @Property(tries = 500)
    void perClientMetrics_hasEntryPerDistinctClient(
            @ForAll("distinctClientIds") List<String> clientIds) {

        // Each client receives at least one update
        for (String clientId : clientIds) {
            metricsCollector.recordLocationUpdate("vehicle-1", clientId);
        }

        Map<String, ClientMetrics> perClientMetrics = metricsCollector.getPerClientMetrics();

        assertEquals(clientIds.size(), perClientMetrics.size(),
                "Number of per-client metric entries should equal the number of distinct clients");
    }

    /**
     * Property 31: For any sequence of updates across multiple clients,
     * one client's updates shall NOT affect another client's metric values.
     *
     * <p><b>Validates: Requirements 9.5</b></p>
     */
    @Property(tries = 500)
    void clientUpdates_doNotInterfereWithOtherClients(
            @ForAll("twoClientUpdateScenarios") TwoClientScenario scenario) {

        // Record updates for client A
        for (int i = 0; i < scenario.clientAUpdates(); i++) {
            metricsCollector.recordLocationUpdate("vehicle-A-" + i, scenario.clientAId());
        }

        // Record updates for client B
        for (int i = 0; i < scenario.clientBUpdates(); i++) {
            metricsCollector.recordLocationUpdate("vehicle-B-" + i, scenario.clientBId());
        }

        Map<String, ClientMetrics> perClientMetrics = metricsCollector.getPerClientMetrics();

        ClientMetrics metricsA = perClientMetrics.get(scenario.clientAId());
        ClientMetrics metricsB = perClientMetrics.get(scenario.clientBId());

        assertNotNull(metricsA, "Client A metrics should exist");
        assertNotNull(metricsB, "Client B metrics should exist");

        assertEquals(scenario.clientAUpdates(), metricsA.getUpdatesReceived(),
                "Client A should have exactly " + scenario.clientAUpdates() + " updates");
        assertEquals(scenario.clientBUpdates(), metricsB.getUpdatesReceived(),
                "Client B should have exactly " + scenario.clientBUpdates() + " updates");
    }

    // --- Arbitraries and data holders ---

    record ClientUpdateSpec(String clientId, int updateCount) {}

    record TwoClientScenario(String clientAId, int clientAUpdates, String clientBId, int clientBUpdates) {}

    /**
     * Generates a list of 1-10 distinct client IDs each with 1-50 updates.
     */
    @Provide
    Arbitrary<List<ClientUpdateSpec>> clientUpdateDistributions() {
        Arbitrary<String> clientIdArb = Arbitraries.integers()
                .between(1, 100)
                .map(i -> "client-" + i);

        Arbitrary<Integer> updateCountArb = Arbitraries.integers().between(1, 50);

        return Combinators.combine(clientIdArb, updateCountArb)
                .as(ClientUpdateSpec::new)
                .list()
                .ofMinSize(2)
                .ofMaxSize(10)
                .uniqueElements(ClientUpdateSpec::clientId);
    }

    /**
     * Generates a list of 2-15 distinct client IDs.
     */
    @Provide
    Arbitrary<List<String>> distinctClientIds() {
        return Arbitraries.integers()
                .between(1, 200)
                .map(i -> "client-" + i)
                .list()
                .ofMinSize(2)
                .ofMaxSize(15)
                .uniqueElements();
    }

    /**
     * Generates two distinct clients with arbitrary update counts.
     */
    @Provide
    Arbitrary<TwoClientScenario> twoClientUpdateScenarios() {
        Arbitrary<Integer> idArb = Arbitraries.integers().between(1, 100);
        Arbitrary<Integer> countArb = Arbitraries.integers().between(1, 50);

        return Combinators.combine(idArb, idArb, countArb, countArb)
                .filter((idA, idB, cA, cB) -> !idA.equals(idB))
                .as((idA, idB, countA, countB) ->
                        new TwoClientScenario("client-" + idA, countA, "client-" + idB, countB));
    }
}
