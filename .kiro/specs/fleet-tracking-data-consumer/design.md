# Design Document: Fleet Tracking Data Consumer

## Overview

The Fleet Tracking Data Consumer is a Java-based web application that simulates realistic client load on an Open Remote-based fleet tracking system. It provides both automated and manual modes for consuming vehicle location data via REST API and WebSocket connections. The application uses Vaadin Flow for the user interface and implements robust error handling and multi-client simulation capabilities.

## Architecture

### High-Level Architecture

The application follows a layered architecture pattern with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────┐
│                    Vaadin Flow UI Layer                      │
│  (Views, Components, User Interactions, Real-time Updates)   │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                   Service Layer                              │
│  (Business Logic, Consumption Orchestration, Metrics)        │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                  Integration Layer                           │
│  (REST Client, WebSocket Client, Connection Management)      │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│              Open Remote API                                 │
│  (https://fms.pcp.com.gr/swagger/)                          │
└─────────────────────────────────────────────────────────────┘
```

### Core Components

#### 1. UI Layer (Vaadin Flow)

**MainView**
- Primary application view containing all UI components
- Hierarchical display of realms and vehicles
- Control panels for Random Mode and Controlled Mode
- Real-time location data display
- Metrics dashboard

**Components:**
- `RealmVehicleTree`: Hierarchical tree component for displaying realms and vehicles with selection capability
- `ConsumptionControlPanel`: Controls for starting/stopping consumption modes
- `LocationDataGrid`: Real-time grid displaying active vehicle locations
- `MetricsPanel`: Dashboard showing connection count, throughput, and other metrics
- `MultiClientConfigPanel`: Configuration panel for multi-client simulation

#### 2. Service Layer

**AuthenticationService**
- Handles authentication with Open Remote API
- Manages session credentials and automatic re-authentication
- Implements retry logic with exponential backoff
- Stores authentication token for subsequent requests

```java
public class AuthenticationService {
    private static final String USERNAME = "alamanos-test";
    private static final String TOKEN = "hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc";
    
    private volatile String sessionToken;
    private final HttpClient httpClient;
    private final ConfigurationService configService;
    
    public CompletableFuture<AuthenticationResult> authenticate();
    public boolean isAuthenticated();
    public void invalidateSession();
    private void scheduleReauthentication();
}
```

**DiscoveryService**
- Discovers available realms from the API
- Retrieves vehicles for each realm
- Implements periodic refresh (60-second intervals)
- Handles discovery failures with retry logic

```java
public class DiscoveryService {
    private final HttpClient httpClient;
    private final AuthenticationService authService;
    private final ScheduledExecutorService scheduler;
    
    public CompletableFuture<List<Realm>> discoverRealms();
    public CompletableFuture<List<Vehicle>> discoverVehicles(String realmId);
    public void startPeriodicRefresh();
    public void stopPeriodicRefresh();
}
```

**ConsumptionOrchestrator**
- Coordinates consumption activities across modes
- Manages WebSocket connections for selected vehicles
- Implements Random Mode with random vehicle selection
- Implements Controlled Mode with user-selected vehicles
- Handles multi-client simulation

```java
public class ConsumptionOrchestrator {
    private final WebSocketClientPool clientPool;
    private final MetricsCollector metricsCollector;
    private final Map<String, ConsumptionSession> activeSessions;
    
    public void startRandomMode();
    public void stopRandomMode();
    public void startControlledMode(Set<Vehicle> vehicles);
    public void stopControlledMode(Set<Vehicle> vehicles);
    public void configureMultiClient(int clientCount);
}
```

**MetricsCollector**
- Collects and aggregates consumption metrics
- Tracks active connections, throughput, update rates
- Provides per-client metrics in multi-client mode
- Updates metrics every 1 second

```java
public class MetricsCollector {
    private final AtomicInteger activeConnections;
    private final AtomicLong totalUpdatesReceived;
    private final Map<String, ClientMetrics> perClientMetrics;
    
    public ConsumptionMetrics getAggregateMetrics();
    public Map<String, ClientMetrics> getPerClientMetrics();
    public void recordLocationUpdate(String vehicleId, String clientId);
    public void recordConnectionChange(int delta);
}
```

**ConfigurationService**
- Reads configuration from application properties
- Provides default values when configuration is missing
- Supports hot reload without application restart
- Validates configuration values

```java
public class ConfigurationService {
    private static final String DEFAULT_ENDPOINT = "https://fms.pcp.com.gr";
    private static final String DEFAULT_USERNAME = "alamanos-test";
    private static final String DEFAULT_TOKEN = "hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc";
    
    private volatile ConfigurationProperties config;
    
    public String getApiEndpoint();
    public String getUsername();
    public String getAuthToken();
    public void reloadConfiguration();
}
```

#### 3. Integration Layer

**OpenRemoteRestClient**
- Handles REST API communication with Open Remote
- Implements authentication requests
- Performs realm and vehicle discovery
- Handles API failures with retry logic

```java
public class OpenRemoteRestClient {
    private final HttpClient httpClient;
    private final String baseUrl;
    
    public CompletableFuture<AuthResponse> authenticate(String username, String token);
    public CompletableFuture<List<RealmDTO>> getRealms(String sessionToken);
    public CompletableFuture<List<VehicleDTO>> getVehicles(String realmId, String sessionToken);
}
```

**WebSocketClientPool**
- Manages pool of WebSocket connections
- Creates connections for vehicle subscriptions
- Implements connection lifecycle management
- Handles reconnection with exponential backoff

```java
public class WebSocketClientPool {
    private final Map<String, WebSocketConnection> connections;
    private final ConnectionFactory connectionFactory;
    private final ExecutorService executor;
    
    public CompletableFuture<WebSocketConnection> createConnection(Vehicle vehicle, String clientId);
    public void closeConnection(String vehicleId);
    public void closeAllConnections();
}
```

**WebSocketConnection**
- Represents a single WebSocket connection to a vehicle
- Subscribes to location data updates
- Parses incoming location data
- Implements automatic reconnection on failure
- Maintains connection state

```java
public class WebSocketConnection {
    private final WebSocket socket;
    private final Vehicle vehicle;
    private final String clientId;
    private final LocationDataHandler dataHandler;
    private final ReconnectionStrategy reconnectionStrategy;
    
    public void subscribe();
    public void close();
    private void handleLocationData(String message);
    private void reconnect();
}
```

**LocationDataHandler**
- Parses incoming location data messages
- Extracts coordinates and metadata
- Notifies UI components of updates
- Handles parsing errors gracefully

```java
public class LocationDataHandler {
    private final ObjectMapper jsonMapper;
    private final List<LocationDataListener> listeners;
    
    public LocationData parse(String json);
    public void notifyListeners(LocationData data);
}
```

### Data Models

**Realm**
```java
public class Realm {
    private String id;
    private String name;
    private List<Vehicle> vehicles;
}
```

**Vehicle**
```java
public class Vehicle {
    private String id;
    private String name;
    private String realmId;
    private VehicleStatus status;
}
```

**LocationData**
```java
public class LocationData {
    private String vehicleId;
    private double latitude;
    private double longitude;
    private Instant timestamp;
    private Map<String, Object> metadata;
}
```

**ConsumptionSession**
```java
public class ConsumptionSession {
    private String sessionId;
    private Vehicle vehicle;
    private String clientId;
    private WebSocketConnection connection;
    private ConsumptionMode mode;
    private Instant startTime;
    private volatile boolean active;
}
```

**ConsumptionMetrics**
```java
public class ConsumptionMetrics {
    private int activeConnections;
    private int activeVehicles;
    private int activeRealms;
    private double updatesPerSecond;
    private Instant lastUpdate;
}
```

**ClientMetrics**
```java
public class ClientMetrics {
    private String clientId;
    private int connections;
    private long updatesReceived;
    private double averageLatency;
}
```

### Concurrency Model

The application uses Java's modern concurrency utilities to handle concurrent operations:

1. **CompletableFuture** for asynchronous API calls and WebSocket operations
2. **ScheduledExecutorService** for periodic tasks (refresh, metrics update)
3. **ConcurrentHashMap** for thread-safe collections of sessions and connections
4. **AtomicInteger/AtomicLong** for thread-safe metrics counters
5. **Vaadin's Push** feature for real-time UI updates from background threads

### Error Handling Strategy

**Retry Logic:**
- API failures: Immediate retry with exponential backoff (1s, 2s, 4s, 8s, max 30s)
- Authentication failures: 3 retry attempts, then display error to user
- WebSocket failures: Automatic reconnection with exponential backoff up to 30s

**Fault Isolation:**
- Failures in one vehicle's consumption do not affect others
- Each WebSocket connection operates independently
- Network outages trigger automatic session resumption when connectivity restored

**Logging:**
- All errors logged with timestamp and detailed context
- INFO level for successful operations
- WARN level for retries
- ERROR level for failures after exhausting retries

### Configuration

Configuration is managed through `application.properties` with support for environment-specific overrides:

```properties
# API Configuration
openremote.api.endpoint=https://fms.pcp.com.gr
openremote.api.username=alamanos-test
openremote.api.token=hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc

# Refresh Intervals (seconds)
openremote.refresh.realms=60
openremote.refresh.vehicles=60
openremote.refresh.metrics=1

# Connection Timeouts (milliseconds)
openremote.connection.timeout=5000
openremote.connection.establishment=2000

# Retry Configuration
openremote.retry.maxAttempts=3
openremote.retry.initialDelay=1000
openremote.retry.maxDelay=30000

# Multi-Client Simulation
openremote.simulation.defaultClients=1
openremote.simulation.maxClients=100
```

### Technology Stack

**Backend:**
- Java 17+ (LTS)
- Spring Boot 3.x for dependency injection and application framework
- Java HTTP Client (java.net.http.HttpClient) for REST communication
- Jakarta WebSocket API for WebSocket connections
- Jackson for JSON parsing
- SLF4J + Logback for logging

**Frontend:**
- Vaadin Flow 24.x
- Vaadin Components (Grid, Tree, Button, TextField, etc.)
- Vaadin Push for real-time updates

**Build & Dependencies:**
- Maven for dependency management
- Spring Boot Maven Plugin for application packaging

**Deployment:**
- Docker for containerization
- Eclipse Temurin JRE 17 as runtime base image
- Docker Compose for local and simple deployments
- Spring Boot Actuator for health checks and monitoring

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property Reflection

After analyzing all acceptance criteria, the following properties were identified as providing unique validation value. Redundancies were eliminated:

- **Property 1-3**: Cover authentication and retry behavior comprehensively
- **Property 4-6**: Cover realm and vehicle discovery with error handling
- **Property 7-9**: Cover both consumption modes and their behaviors
- **Property 10-12**: Cover WebSocket lifecycle and data handling
- **Property 13-15**: Cover multi-client simulation comprehensively
- **Property 16-18**: Cover UI display requirements
- **Property 19-21**: Cover metrics collection and display
- **Property 22-24**: Cover error handling and resilience
- **Property 25-27**: Cover configuration management

Properties related to timing constraints (2-second connection establishment, 1-second closure, 500ms display update) are tested via example-based unit tests rather than property-based tests since they are specific performance requirements.

Properties related to infrastructure checks (UI controls exist, Java is used, Vaadin is used) are validated through smoke tests and code review rather than property-based tests.

### Property 1: Authentication Retry on Failure

*For any* authentication failure response, the system SHALL log the error with timestamp and details, then retry authentication with exponential backoff starting at 1 second, not exceeding 30 seconds maximum delay.

**Validates: Requirements 1.3**

### Property 2: Session Credential Storage

*For any* successful authentication response, the system SHALL store the session credentials and use them in all subsequent API requests without requiring re-authentication until session expiration.

**Validates: Requirements 1.4**

### Property 3: Realm Discovery After Authentication

*For any* successful authentication, the system SHALL retrieve the complete list of available realms from the API.

**Validates: Requirements 2.1**

### Property 4: Realm Display Completeness

*For any* retrieved realm list, all realms SHALL appear in the user interface display.

**Validates: Requirements 2.2**

### Property 5: Realm Retrieval Retry

*For any* realm retrieval failure, the system SHALL log the error with timestamp and retry within the configured interval.

**Validates: Requirements 2.3**

### Property 6: Vehicle Discovery Per Realm

*For any* discovered realm, the system SHALL retrieve all associated vehicles from the API.

**Validates: Requirements 3.1**

### Property 7: Vehicle Grouping by Realm

*For any* combination of realms and vehicles, the UI SHALL display vehicles grouped by their associated realm in a hierarchical structure.

**Validates: Requirements 3.2**

### Property 8: Vehicle Retrieval Retry

*For any* vehicle retrieval failure for a specific realm, the system SHALL log the error and retry without affecting other realms.

**Validates: Requirements 3.3**

### Property 9: Random Vehicle Selection

*For any* available set of realms and vehicles when Random Mode is activated, the system SHALL select vehicles randomly across all realms.

**Validates: Requirements 4.1**

### Property 10: Location Data Consumption in Random Mode

*For any* vehicle selected in Random Mode with an established WebSocket connection, the system SHALL consume and process Location_Data updates in real-time.

**Validates: Requirements 4.3**

### Property 11: Individual Vehicle Selection

*For any* vehicle in the discovered vehicle list, the user SHALL be able to select that vehicle through the UI for controlled consumption.

**Validates: Requirements 5.1**

### Property 12: Cascading Realm Selection

*For any* realm selection by the user, the system SHALL automatically select all vehicles within that realm.

**Validates: Requirements 5.3**

### Property 13: Location Data Consumption in Controlled Mode

*For any* user-selected vehicle with an established WebSocket connection in Controlled Mode, the system SHALL consume and process Location_Data updates in real-time.

**Validates: Requirements 5.5**

### Property 14: Consumption Status Display

*For any* set of selected vehicles, the UI SHALL display the current consumption status (active/inactive) for each vehicle.

**Validates: Requirements 5.7**

### Property 15: Location Subscription on Connection

*For any* vehicle with a newly established WebSocket connection, the system SHALL send a subscription message for that vehicle's Location_Data updates.

**Validates: Requirements 6.1**

### Property 16: Location Data Parsing

*For any* received Location_Data message containing geographic coordinates and metadata, the system SHALL successfully parse the coordinates and metadata into structured data.

**Validates: Requirements 6.2**

### Property 17: WebSocket Reconnection on Failure

*For any* interrupted WebSocket connection during an active consumption session, the system SHALL attempt to re-establish the connection with exponential backoff up to 30 seconds maximum delay.

**Validates: Requirements 6.4**

### Property 18: Multi-Client Connection Creation

*For any* configured number of simulated clients, the system SHALL create independent WebSocket connections for each client when multi-client mode is activated.

**Validates: Requirements 7.2**

### Property 19: Vehicle Distribution Across Clients

*For any* set of vehicles and configured number of clients, the system SHALL distribute vehicle subscriptions evenly across all simulated clients.

**Validates: Requirements 7.3**

### Property 20: Independent Client Data Consumption

*For any* multi-client configuration with active subscriptions, each simulated client SHALL consume Location_Data independently without interference from other clients.

**Validates: Requirements 7.4**

### Property 21: Metric Aggregation Across Clients

*For any* multi-client configuration with active consumption, the system SHALL correctly aggregate metrics (connections, updates, throughput) across all simulated clients.

**Validates: Requirements 7.5**

### Property 22: Active Vehicle Display

*For any* set of vehicles with active consumption sessions, the UI SHALL display all actively consumed vehicles in the vehicle list.

**Validates: Requirements 8.1**

### Property 23: Latest Coordinate Display

*For any* received Location_Data for a vehicle, the UI SHALL display the latest geographic coordinates for that vehicle.

**Validates: Requirements 8.2**

### Property 24: Timestamp Display

*For any* vehicle with received Location_Data, the UI SHALL display the timestamp of the most recent update.

**Validates: Requirements 8.3**

### Property 25: Realm Association Display

*For any* vehicle in the UI, the system SHALL display the realm to which that vehicle belongs.

**Validates: Requirements 8.4**

### Property 26: Metadata Field Display

*For any* Location_Data containing additional metadata fields, the UI SHALL display the relevant metadata fields for that vehicle.

**Validates: Requirements 8.5**

### Property 27: Active Connection Count Display

*For any* number of active WebSocket connections, the metrics panel SHALL display the correct total count.

**Validates: Requirements 9.1**

### Property 28: Update Rate Display

*For any* rate of Location_Data updates per second, the metrics panel SHALL display the correct throughput value.

**Validates: Requirements 9.2**

### Property 29: Active Vehicle Count Display

*For any* number of vehicles currently being consumed, the metrics panel SHALL display the correct count.

**Validates: Requirements 9.3**

### Property 30: Active Realm Count Display

*For any* number of realms with at least one active vehicle consumption, the metrics panel SHALL display the correct count.

**Validates: Requirements 9.4**

### Property 31: Per-Client Metrics Display

*For any* multi-client configuration with active consumption, the metrics panel SHALL display metrics separately for each simulated client.

**Validates: Requirements 9.5**

### Property 32: Hierarchical Realm-Vehicle Display

*For any* combination of realms and their associated vehicles, the UI SHALL display them in a hierarchical tree structure with realms as parent nodes and vehicles as child nodes.

**Validates: Requirements 10.2**

### Property 33: Real-Time UI Updates

*For any* received Location_Data update, the UI SHALL reflect the update without requiring a manual page refresh.

**Validates: Requirements 10.5**

### Property 34: API Request Failure Logging

*For any* failed API request, the system SHALL log the error with timestamp and detailed error information.

**Validates: Requirements 12.1**

### Property 35: WebSocket Reconnection with Backoff

*For any* WebSocket connection failure, the system SHALL attempt reconnection using exponential backoff with delays increasing geometrically up to a maximum of 30 seconds.

**Validates: Requirements 12.2**

### Property 36: Session Resumption After Outage

*For any* set of active consumption sessions when network connectivity is restored after an outage, the system SHALL automatically resume all previously active sessions.

**Validates: Requirements 12.3**

### Property 37: Isolated Vehicle Failure Handling

*For any* vehicle in a set of actively consumed vehicles, if consumption fails for that specific vehicle, the system SHALL continue consuming data from all other vehicles without interruption.

**Validates: Requirements 12.5**

### Property 38: API Endpoint Configuration Reading

*For any* valid API endpoint URL provided in configuration, the system SHALL read and use that endpoint for API communication.

**Validates: Requirements 13.1**

### Property 39: Authentication Credentials Configuration Reading

*For any* valid authentication credentials provided in configuration, the system SHALL read and use those credentials for authentication.

**Validates: Requirements 13.2**

### Property 40: Hot Configuration Reload

*For any* configuration change made while the application is running, the system SHALL apply the new configuration without requiring an application restart.

**Validates: Requirements 13.4**

### Property 41: Invalid Configuration Handling

*For any* invalid configuration value, the system SHALL log the error with details and fall back to using the default value for that configuration parameter.

**Validates: Requirements 13.5**

### Property 42: Docker Multi-Stage Build Image Optimization

*For any* Docker image built using the multi-stage Dockerfile, the final runtime image SHALL contain only the compiled JAR and JRE runtime, excluding build tools and source code.

**Validates: Requirements 14.7**

### Property 43: Docker Port Exposure Configuration

*For any* configured port value via SERVER_PORT environment variable, the Docker container SHALL expose the application on that port.

**Validates: Requirements 14.3, 14.4**

### Property 44: Docker Environment Variable Configuration

*For any* Spring Boot configuration property with a corresponding environment variable, when that environment variable is set, the application SHALL use the environment variable value instead of the default.

**Validates: Requirements 14.4**

### Property 45: Docker Container Web Accessibility

*For any* Docker container started with proper port mapping, the application SHALL be accessible via web browser at the exposed host port.

**Validates: Requirements 14.6**

### Property 46: Docker Graceful Shutdown Handling

*For any* SIGTERM signal received by the Docker container, the application SHALL initiate graceful shutdown, closing active WebSocket connections cleanly and completing in-flight requests before exit.

**Validates: Requirements 14.8**

### Property 47: Docker Compose Service Startup

*For any* Docker Compose deployment using the provided docker-compose.yml, running `docker-compose up` SHALL start the application container with all configured environment variables and port mappings.

**Validates: Requirements 14.5**

## Deployment Architecture

The application is designed to run in a containerized environment using Docker, providing consistent deployment across different environments (development, staging, production).

### Container Architecture

```
┌────────────────────────────────────────────────────────┐
│              Docker Host                                │
│                                                         │
│  ┌──────────────────────────────────────────────────┐ │
│  │  Fleet Tracking Consumer Container                │ │
│  │                                                    │ │
│  │  ┌──────────────────────────────────────────┐    │ │
│  │  │  Java Runtime (Eclipse Temurin)          │    │ │
│  │  │                                           │    │ │
│  │  │  ┌────────────────────────────────────┐  │    │ │
│  │  │  │  Spring Boot Application           │  │    │ │
│  │  │  │  - Vaadin UI                       │  │    │ │
│  │  │  │  - WebSocket Clients               │  │    │ │
│  │  │  │  - REST Client                     │  │    │ │
│  │  │  └────────────────────────────────────┘  │    │ │
│  │  │                                           │    │ │
│  │  │  Port: 8080 (configurable)               │    │ │
│  │  └──────────────────────────────────────────┘    │ │
│  │                                                    │ │
│  │  Volume Mounts:                                   │ │
│  │  - /app/logs (application logs)                   │ │
│  │  - /app/config (optional external config)         │ │
│  └──────────────────────────────────────────────────┘ │
│                                                         │
│  Port Mapping: HOST:8080 -> CONTAINER:8080            │
└────────────────────────────────────────────────────────┘
```

### Dockerfile Design

The application uses a multi-stage Docker build approach to optimize image size and security:

**Stage 1: Build Stage**
- Base image: Maven with Eclipse Temurin JDK 17
- Copies source code and POM file
- Executes Maven build to create executable JAR
- Produces production-ready artifact

**Stage 2: Runtime Stage**
- Base image: Eclipse Temurin JRE 17 (smaller than JDK)
- Copies only the compiled JAR from build stage
- Minimal attack surface with no build tools
- Non-root user for running the application

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Create non-root user
RUN useradd -m -u 1001 appuser && chown -R appuser:appuser /app
USER appuser

# Expose default port
EXPOSE 8080

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Environment Variable Configuration

Spring Boot properties are mapped to environment variables for Docker deployment:

| Environment Variable | Spring Property | Default Value | Description |
|---------------------|----------------|---------------|-------------|
| `SERVER_PORT` | `server.port` | `8080` | Application HTTP port |
| `OPENREMOTE_API_ENDPOINT` | `openremote.api.endpoint` | `https://fms.pcp.com.gr` | Open Remote API base URL |
| `OPENREMOTE_API_USERNAME` | `openremote.api.username` | `alamanos-test` | API authentication username |
| `OPENREMOTE_API_TOKEN` | `openremote.api.token` | `hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc` | API authentication token |
| `OPENREMOTE_REFRESH_REALMS` | `openremote.refresh.realms` | `60` | Realm refresh interval (seconds) |
| `OPENREMOTE_REFRESH_VEHICLES` | `openremote.refresh.vehicles` | `60` | Vehicle refresh interval (seconds) |
| `OPENREMOTE_REFRESH_METRICS` | `openremote.refresh.metrics` | `1` | Metrics refresh interval (seconds) |
| `OPENREMOTE_CONNECTION_TIMEOUT` | `openremote.connection.timeout` | `5000` | HTTP connection timeout (ms) |
| `OPENREMOTE_CONNECTION_ESTABLISHMENT` | `openremote.connection.establishment` | `2000` | WebSocket establishment timeout (ms) |
| `OPENREMOTE_RETRY_MAXATTEMPTS` | `openremote.retry.maxAttempts` | `3` | Maximum retry attempts |
| `OPENREMOTE_RETRY_INITIALDELAY` | `openremote.retry.initialDelay` | `1000` | Initial retry delay (ms) |
| `OPENREMOTE_RETRY_MAXDELAY` | `openremote.retry.maxDelay` | `30000` | Maximum retry delay (ms) |
| `OPENREMOTE_SIMULATION_DEFAULTCLIENTS` | `openremote.simulation.defaultClients` | `1` | Default client count |
| `OPENREMOTE_SIMULATION_MAXCLIENTS` | `openremote.simulation.maxClients` | `100` | Maximum allowed clients |
| `JAVA_OPTS` | N/A | (empty) | Additional JVM options |

### Docker Compose Configuration

A Docker Compose file is provided for simplified deployment:

```yaml
version: '3.8'

services:
  fleet-consumer:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: fleet-tracking-consumer
    ports:
      - "8080:8080"
    environment:
      - SERVER_PORT=8080
      - OPENREMOTE_API_ENDPOINT=https://fms.pcp.com.gr
      - OPENREMOTE_API_USERNAME=alamanos-test
      - OPENREMOTE_API_TOKEN=hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc
      - JAVA_OPTS=-Xmx512m -Xms256m
    volumes:
      - ./logs:/app/logs
      - ./config:/app/config
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
```

### Graceful Shutdown

Spring Boot is configured to handle container lifecycle events properly:

**SIGTERM Handling:**
- Docker sends SIGTERM when stopping a container
- Spring Boot intercepts SIGTERM and initiates graceful shutdown
- Active WebSocket connections are closed cleanly
- In-flight requests are allowed to complete
- Shutdown timeout configured to 30 seconds

**Configuration:**
```properties
# Graceful shutdown configuration
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```

**Shutdown Sequence:**
1. Container receives SIGTERM signal
2. Spring Boot stops accepting new connections
3. Active WebSocket connections receive close frames
4. Active sessions complete or timeout after 30 seconds
5. Application exits cleanly with exit code 0

### Port Configuration

**Default Port:** 8080 (standard HTTP port for containerized applications)

**Configuration Options:**
- Via environment variable: `SERVER_PORT=9090`
- Via Docker Compose port mapping: `"9090:8080"`
- Via application.properties: `server.port=8080`

**Port Mapping Examples:**
```bash
# Run on default port 8080
docker run -p 8080:8080 fleet-tracking-consumer

# Run on custom port 9090
docker run -p 9090:8080 -e SERVER_PORT=8080 fleet-tracking-consumer

# Run on random host port
docker run -P fleet-tracking-consumer
```

### Volume Mounts

**Log Volume:**
- Mount point: `/app/logs`
- Purpose: Persist application logs outside container
- Recommended for production to enable log analysis and troubleshooting

**Config Volume:**
- Mount point: `/app/config`
- Purpose: Optional external configuration files
- Allows runtime configuration without rebuilding image

**Example:**
```bash
docker run -v $(pwd)/logs:/app/logs \
           -v $(pwd)/config:/app/config \
           -p 8080:8080 \
           fleet-tracking-consumer
```

### Container Resource Limits

**Recommended Resource Allocation:**
- **Memory:** 512MB minimum, 1GB recommended
- **CPU:** 0.5 cores minimum, 1 core recommended
- **Disk:** 100MB for image, additional space for logs

**Docker Compose Resource Configuration:**
```yaml
services:
  fleet-consumer:
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 1G
        reservations:
          cpus: '0.5'
          memory: 512M
```

### Health Checks

Spring Boot Actuator health endpoint is exposed for container orchestration:

**Health Endpoint:** `http://localhost:8080/actuator/health`

**Health Check Components:**
- Application status (UP/DOWN)
- Disk space
- Optional: Custom health indicators for WebSocket connections

**Kubernetes Liveness/Readiness:**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
```

## Security Considerations

1. **Authentication Token Storage**: The authentication token is stored in memory only, never persisted to disk
2. **Secure Communication**: All communication with the Open Remote API should use HTTPS
3. **Configuration Security**: Sensitive configuration values (tokens, credentials) should be externalized and not committed to version control
4. **Input Validation**: All data received from the API should be validated before processing
5. **Resource Limits**: Multi-client simulation should have configurable maximum limits to prevent resource exhaustion
6. **Container Security**:
   - Non-root user (UID 1001) runs the application inside the container
   - Minimal base image (Eclipse Temurin JRE) reduces attack surface
   - Multi-stage build excludes build tools from runtime image
   - Sensitive environment variables should use Docker secrets or secure vault solutions
   - Container image should be scanned for vulnerabilities before deployment
7. **Network Security**:
   - Only necessary ports (8080) are exposed
   - Consider running behind reverse proxy (nginx, Traefik) for TLS termination
   - Container should run in isolated Docker network when deployed with other services

## Performance Considerations

1. **Connection Pooling**: WebSocket connections are pooled and reused where possible
2. **Asynchronous Operations**: All API calls and WebSocket operations are asynchronous to prevent blocking
3. **Metrics Collection**: Metrics are collected efficiently using atomic operations to minimize overhead
4. **UI Updates**: Vaadin Push is used for efficient real-time UI updates without polling
5. **Memory Management**: Inactive sessions are cleaned up promptly to prevent memory leaks
6. **Container Performance**:
   - JVM heap size configured via `JAVA_OPTS` environment variable (default: -Xmx512m -Xms256m)
   - Container memory limits prevent host resource exhaustion
   - Multi-stage build reduces image size for faster deployment
   - Health checks prevent routing traffic to unhealthy containers
   - Consider container CPU limits to ensure fair resource allocation in multi-container environments

## Future Enhancements

1. **Data Recording**: Ability to record consumed location data for later analysis
2. **Visualization**: Map-based visualization of vehicle locations
3. **Filtering**: Advanced filtering capabilities for vehicle selection
4. **Playback**: Replay recorded data at various speeds
5. **Statistics**: Historical statistics and trend analysis
6. **Alerting**: Configurable alerts for specific conditions (connection failures, high latency)
7. **Container Orchestration**:
   - Kubernetes deployment manifests with auto-scaling
   - Helm chart for simplified Kubernetes deployment
   - Horizontal pod autoscaling based on CPU/memory metrics
   - Service mesh integration (Istio, Linkerd) for advanced traffic management
8. **Observability**:
   - Prometheus metrics export
   - Distributed tracing with OpenTelemetry
   - Centralized logging with ELK stack or Loki
   - Grafana dashboards for monitoring
