package com.fms.consumer.service;

import com.fms.consumer.integration.WebSocketClientPool;
import com.fms.consumer.model.ConsumptionMetrics;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.model.VehicleStatus;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for metric aggregation across multiple simulated clients.
 *
 * <p><b>Validates: Requirements 7.5</b></p>
 *
 * <p>Property 21: Metric Aggregation Across Clients —
 * "For any multi-client configuration with active consumption, the system SHALL
 * correctly aggregate metrics (connections, updates, throughput) across all
 * simulated clients."</p>
 *
 * <p>Uses a real {@link MetricsCollector} (not mocked) to verify that aggregate
 * metrics reflect total connections across all clients.</p>
 */
class MetricAggregationPropertyTest {

    private MetricsCollector metricsCollector;
    private WebSocketClientPool clientPool; private LocationPollingService locationPollingService;
    private DiscoveryService discoveryService;
    private ConfigurationService configService;
    private ConsumptionOrchestrator orchestrator;

    @BeforeProperty
    void setUp() {
        metricsCollector = new MetricsCollector();
    }

    @AfterProperty
    void tearDown() {
        if (metricsCollector != null) {
            metricsCollector.shutdown();
        }
    }

    @BeforeTry
    void setUpTry() {
        clientPool = mock(WebSocketClientPool.class); locationPollingService = mock(LocationPollingService.class);
        discoveryService = mock(DiscoveryService.class);
        configService = mock(ConfigurationService.class);

        when(configService.getSimulationMaxClients()).thenReturn(100);

        metricsCollector.reset();

        orchestrator = new ConsumptionOrchestrator(clientPool, locationPollingService, metricsCollector, discoveryService, configService);
    }

    /**
     * For any N clients and M vehicles, when controlled mode is started,
     * MetricsCollector SHALL record exactly N×M connection changes (+1 each),
     * resulting in N×M total active connections in the aggregate metrics.
     *
     * <p><b>Validates: Requirements 7.5</b></p>
     */
    @Property(tries = 200)
    void controlledMode_aggregatesConnectionsAcrossClients(
            @ForAll("clientCounts") int clientCount,
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        // Configure multi-client
        orchestrator.configureMultiClient(clientCount);

        // Start controlled mode with the given vehicles
        orchestrator.startControlledMode(vehicles);

        int expectedConnections = clientCount * vehicles.size();

        // Verify using the real MetricsCollector
        assertEquals(expectedConnections, metricsCollector.getActiveConnections(),
                "Active connections should be clientCount × vehicleCount = "
                        + clientCount + " × " + vehicles.size() + " = " + expectedConnections);

        // Also verify via getAggregateMetrics
        ConsumptionMetrics aggregateMetrics = metricsCollector.getAggregateMetrics();
        assertEquals(expectedConnections, aggregateMetrics.getActiveConnections(),
                "Aggregate metrics activeConnections should equal " + expectedConnections);
    }

    /**
     * For any N clients and M vehicles, when controlled mode is started,
     * the aggregate metrics SHALL reflect the correct number of active vehicles
     * (which equals M, since each unique vehicle is tracked once regardless of client count).
     *
     * <p><b>Validates: Requirements 7.5</b></p>
     */
    @Property(tries = 200)
    void controlledMode_aggregatesVehicleCountCorrectly(
            @ForAll("clientCounts") int clientCount,
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(vehicles);

        ConsumptionMetrics aggregateMetrics = metricsCollector.getAggregateMetrics();

        assertEquals(vehicles.size(), aggregateMetrics.getActiveVehicles(),
                "Aggregate metrics activeVehicles should equal the number of distinct vehicles = "
                        + vehicles.size());
    }

    /**
     * For any N clients and M vehicles across R distinct realms, when controlled mode
     * is started, the aggregate metrics SHALL reflect the correct number of active realms.
     *
     * <p><b>Validates: Requirements 7.5</b></p>
     */
    @Property(tries = 200)
    void controlledMode_aggregatesRealmCountCorrectly(
            @ForAll("clientCounts") int clientCount,
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(vehicles);

        int expectedRealms = (int) vehicles.stream()
                .map(Vehicle::getRealmId)
                .filter(r -> r != null && !r.isBlank())
                .distinct()
                .count();

        ConsumptionMetrics aggregateMetrics = metricsCollector.getAggregateMetrics();

        assertEquals(expectedRealms, aggregateMetrics.getActiveRealms(),
                "Aggregate metrics activeRealms should equal the number of distinct realms = "
                        + expectedRealms);
    }

    // --- Generators ---

    /**
     * Generates client counts between 1 and 10 (kept small to avoid excessive session creation).
     */
    @Provide
    Arbitrary<Integer> clientCounts() {
        return Arbitraries.integers().between(1, 10);
    }

    /**
     * Generates non-empty sets of vehicles with unique IDs (1 to 8 vehicles).
     */
    @Provide
    Arbitrary<Set<Vehicle>> vehicleSets() {
        Arbitrary<Vehicle> vehicleArbitrary = Combinators.combine(
                vehicleIds(),
                vehicleNames(),
                realmIds()
        ).as((id, name, realmId) -> new Vehicle(id, name, realmId, VehicleStatus.ACTIVE));

        return vehicleArbitrary.set().ofMinSize(1).ofMaxSize(8);
    }

    private Arbitrary<String> vehicleIds() {
        return Arbitraries.integers()
                .between(1, 200)
                .map(i -> "v-" + i);
    }

    private Arbitrary<String> vehicleNames() {
        return Arbitraries.of(
                "Fleet Truck 1", "Fleet Truck 2", "Fleet Truck 3",
                "City Bus Alpha", "City Bus Beta", "City Bus Gamma",
                "Delivery Van A", "Delivery Van B",
                "Heavy Loader X", "Light Cargo Y",
                "Emergency Vehicle", "Transport Unit 42"
        );
    }

    private Arbitrary<String> realmIds() {
        return Arbitraries.of(
                "realm-1", "realm-2", "realm-3",
                "production", "staging",
                "fleet-management", "eu-west-1"
        );
    }
}
