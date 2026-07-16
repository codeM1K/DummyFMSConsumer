# Implementation Plan: Fleet Tracking Data Consumer

## Overview

This implementation plan converts the fleet tracking data consumer design into discrete coding tasks. The application is a Java/Spring Boot web application with Vaadin Flow UI that consumes vehicle location data from an Open Remote-based fleet tracking system. Implementation follows a bottom-up approach: foundation (configuration, models, integration) → services (authentication, discovery, consumption) → UI layer → integration and testing.

## Tasks

- [x] 1. Set up project structure and core data models
  - [x] 1.1 Create Maven project structure with Spring Boot and Vaadin dependencies
    - Initialize Spring Boot 3.x project with Vaadin Flow 24.x starter
    - Configure Maven POM with required dependencies: Spring Boot, Vaadin, Jackson, WebSocket API, SLF4J
    - Set up application.properties with default configuration values
    - _Requirements: 11.1, 13.3_

  - [x] 1.2 Implement core data model classes
    - Create `Realm` class with id, name, and vehicles list
    - Create `Vehicle` class with id, name, realmId, and status
    - Create `LocationData` class with vehicleId, coordinates, timestamp, metadata
    - Create `ConsumptionSession` class to track active sessions
    - Create `ConsumptionMetrics` and `ClientMetrics` classes for metrics tracking
    - _Requirements: 2.1, 3.1, 6.2, 9.1-9.5_

  - [x] 1.3 Write unit tests for data model classes
    - Test data model constructors, getters, setters
    - Test data validation logic
    - Test edge cases (null values, empty collections)
    - _Requirements: 2.1, 3.1, 6.2_

- [x] 2. Implement configuration management
  - [x] 2.1 Create ConfigurationService
    - Implement service to read configuration from application.properties
    - Provide default values: endpoint "https://fms.pcp.com.gr", username "alamanos-test", token "hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc"
    - Implement configuration validation logic
    - Implement hot reload capability using Spring's @RefreshScope
    - _Requirements: 13.1, 13.2, 13.3, 13.4_

  - [x] 2.2 Write property test for configuration service
    - **Property 38: API Endpoint Configuration Reading**
    - **Validates: Requirements 13.1**

  - [x] 2.3 Write property test for credential configuration
    - **Property 39: Authentication Credentials Configuration Reading**
    - **Validates: Requirements 13.2**

  - [x] 2.4 Write property test for invalid configuration handling
    - **Property 41: Invalid Configuration Handling**
    - **Validates: Requirements 13.5**

  - [x] 2.5 Write unit tests for hot configuration reload
    - Test configuration reloading without restart
    - Test fallback to defaults on invalid configuration
    - _Requirements: 13.4, 13.5_

- [x] 3. Implement REST API integration layer
  - [x] 3.1 Create OpenRemoteRestClient
    - Implement REST client using Java HttpClient for API communication
    - Create authenticate method with username and token parameters
    - Create getRealms method to fetch all available realms
    - Create getVehicles method to fetch vehicles for a specific realm
    - Implement error handling and logging for API failures
    - _Requirements: 1.1, 1.2, 2.1, 3.1, 11.2, 12.1_

  - [x] 3.2 Write unit tests for OpenRemoteRestClient
    - Test successful API calls with mock responses
    - Test API error handling
    - Test request formatting and header inclusion
    - _Requirements: 1.1, 2.1, 3.1_

- [x] 4. Implement authentication service
  - [x] 4.1 Create AuthenticationService
    - Implement authenticate method returning CompletableFuture
    - Implement retry logic with exponential backoff (1s, 2s, 4s, 8s, max 30s)
    - Store session token in volatile field for thread-safe access
    - Implement automatic re-authentication on session expiration
    - Integrate with ConfigurationService for credentials
    - Integrate with OpenRemoteRestClient for API calls
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 4.2 Write property test for authentication retry behavior
    - **Property 1: Authentication Retry on Failure**
    - **Validates: Requirements 1.3**

  - [x] 4.3 Write property test for session credential storage
    - **Property 2: Session Credential Storage**
    - **Validates: Requirements 1.4**

  - [x] 4.4 Write unit tests for authentication service
    - Test successful authentication flow
    - Test authentication failure after 3 attempts with user notification
    - Test automatic re-authentication on session expiration
    - _Requirements: 1.3, 1.4, 1.5_

- [x] 5. Checkpoint - Verify foundation layer
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement discovery service
  - [x] 6.1 Create DiscoveryService
    - Implement discoverRealms method returning CompletableFuture<List<Realm>>
    - Implement discoverVehicles method for specific realm
    - Implement periodic refresh using ScheduledExecutorService (60-second intervals)
    - Implement retry logic for failed discovery attempts (10-second retry interval)
    - Integrate with AuthenticationService for session tokens
    - Integrate with OpenRemoteRestClient for API calls
    - _Requirements: 2.1, 2.3, 2.4, 3.1, 3.3, 3.4_

  - [x] 6.2 Write property test for realm discovery after authentication
    - **Property 3: Realm Discovery After Authentication**
    - **Validates: Requirements 2.1**

  - [x] 6.3 Write property test for realm retrieval retry
    - **Property 5: Realm Retrieval Retry**
    - **Validates: Requirements 2.3**

  - [x] 6.4 Write property test for vehicle discovery per realm
    - **Property 6: Vehicle Discovery Per Realm**
    - **Validates: Requirements 3.1**

  - [x] 6.5 Write property test for vehicle retrieval retry
    - **Property 8: Vehicle Retrieval Retry**
    - **Validates: Requirements 3.3**

  - [x] 6.6 Write unit tests for discovery service
    - Test periodic refresh mechanism
    - Test realm and vehicle discovery success paths
    - Test error handling and retry logic
    - _Requirements: 2.1, 2.3, 2.4, 3.1, 3.3, 3.4_

- [x] 7. Implement WebSocket integration layer
  - [x] 7.1 Create LocationDataHandler
    - Implement parse method to extract coordinates and metadata from JSON
    - Implement listener pattern for notifying UI of updates
    - Use Jackson ObjectMapper for JSON parsing
    - Implement error handling for malformed messages
    - _Requirements: 6.2, 11.3_

  - [x] 7.2 Create WebSocketConnection
    - Implement WebSocket connection for single vehicle using Jakarta WebSocket API
    - Implement subscribe method to send subscription message
    - Implement close method to gracefully close connection
    - Implement handleLocationData callback for incoming messages
    - Implement reconnection logic with exponential backoff (max 30 seconds)
    - Integrate with LocationDataHandler for message parsing
    - _Requirements: 6.1, 6.4, 6.5, 11.3, 12.2_

  - [x] 7.3 Create WebSocketClientPool
    - Implement connection pool using ConcurrentHashMap
    - Implement createConnection method returning CompletableFuture
    - Implement closeConnection method for specific vehicle
    - Implement closeAllConnections method
    - Use ExecutorService for asynchronous connection management
    - _Requirements: 4.2, 4.4, 5.4, 5.6, 11.4_

  - [x] 7.4 Write property test for location data parsing
    - **Property 16: Location Data Parsing**
    - **Validates: Requirements 6.2**

  - [x] 7.5 Write property test for WebSocket reconnection
    - **Property 17: WebSocket Reconnection on Failure**
    - **Validates: Requirements 6.4**

  - [x] 7.6 Write property test for location subscription
    - **Property 15: Location Subscription on Connection**
    - **Validates: Requirements 6.1**

  - [x] 7.7 Write unit tests for WebSocket components
    - Test connection establishment timing (within 2 seconds)
    - Test connection closure timing (within 1 second)
    - Test message parsing and error handling
    - _Requirements: 4.2, 4.4, 5.4, 5.6, 6.1, 6.2, 6.4_

- [x] 8. Implement metrics collection service
  - [x] 8.1 Create MetricsCollector
    - Implement metrics tracking using AtomicInteger and AtomicLong for thread safety
    - Implement recordLocationUpdate method
    - Implement recordConnectionChange method
    - Implement getAggregateMetrics method
    - Implement getPerClientMetrics method for multi-client mode
    - Use ScheduledExecutorService to update metrics every 1 second
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 11.4_

  - [x] 8.2 Write property test for active connection count
    - **Property 27: Active Connection Count Display**
    - **Validates: Requirements 9.1**

  - [x] 8.3 Write property test for update rate calculation
    - **Property 28: Update Rate Display**
    - **Validates: Requirements 9.2**

  - [x] 8.4 Write property test for active vehicle count
    - **Property 29: Active Vehicle Count Display**
    - **Validates: Requirements 9.3**

  - [x] 8.5 Write property test for active realm count
    - **Property 30: Active Realm Count Display**
    - **Validates: Requirements 9.4**

  - [x] 8.6 Write property test for per-client metrics
    - **Property 31: Per-Client Metrics Display**
    - **Validates: Requirements 9.5**

  - [x] 8.7 Write unit tests for metrics collector
    - Test metrics aggregation accuracy
    - Test concurrent metric updates
    - Test metric reset and clearing
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

- [x] 9. Checkpoint - Verify service and integration layers
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Implement consumption orchestration service
  - [x] 10.1 Create ConsumptionOrchestrator
    - Implement startRandomMode method with random vehicle selection
    - Implement stopRandomMode method
    - Implement startControlledMode method for user-selected vehicles
    - Implement stopControlledMode method for specific vehicles
    - Implement configureMultiClient method to set number of simulated clients
    - Manage active sessions using ConcurrentHashMap
    - Integrate with WebSocketClientPool for connection management
    - Integrate with MetricsCollector for metrics tracking
    - _Requirements: 4.1, 4.5, 4.6, 5.1, 5.2, 5.3, 5.4, 5.6, 7.1, 7.2, 7.3, 7.4_

  - [x] 10.2 Write property test for random vehicle selection
    - **Property 9: Random Vehicle Selection**
    - **Validates: Requirements 4.1**

  - [x] 10.3 Write property test for location data consumption in random mode
    - **Property 10: Location Data Consumption in Random Mode**
    - **Validates: Requirements 4.3**

  - [x] 10.4 Write property test for individual vehicle selection
    - **Property 11: Individual Vehicle Selection**
    - **Validates: Requirements 5.1**

  - [x] 10.5 Write property test for cascading realm selection
    - **Property 12: Cascading Realm Selection**
    - **Validates: Requirements 5.3**

  - [x] 10.6 Write property test for controlled mode consumption
    - **Property 13: Location Data Consumption in Controlled Mode**
    - **Validates: Requirements 5.5**

  - [x] 10.7 Write property test for multi-client connection creation
    - **Property 18: Multi-Client Connection Creation**
    - **Validates: Requirements 7.2**

  - [x] 10.8 Write property test for vehicle distribution
    - **Property 19: Vehicle Distribution Across Clients**
    - **Validates: Requirements 7.3**

  - [x] 10.9 Write property test for independent client consumption
    - **Property 20: Independent Client Data Consumption**
    - **Validates: Requirements 7.4**

  - [x] 10.10 Write property test for metric aggregation
    - **Property 21: Metric Aggregation Across Clients**
    - **Validates: Requirements 7.5**

  - [x] 10.11 Write unit tests for consumption orchestrator
    - Test mode transitions (start/stop Random Mode, Controlled Mode)
    - Test multi-client configuration
    - Test session management
    - _Requirements: 4.1, 4.5, 4.6, 5.4, 5.6, 7.1, 7.2, 7.3, 7.4_

- [x] 11. Implement error handling and resilience
  - [x] 11.1 Enhance all services with comprehensive error handling
    - Add error logging with timestamps and detailed context throughout all services
    - Implement exponential backoff for retries in all network operations
    - Implement session resumption logic in ConsumptionOrchestrator for network recovery
    - Implement isolated failure handling to prevent cascade failures
    - _Requirements: 12.1, 12.2, 12.3, 12.5_

  - [x] 11.2 Write property test for API request failure logging
    - **Property 34: API Request Failure Logging**
    - **Validates: Requirements 12.1**

  - [x] 11.3 Write property test for session resumption after outage
    - **Property 36: Session Resumption After Outage**
    - **Validates: Requirements 12.3**

  - [x] 11.4 Write property test for isolated vehicle failure handling
    - **Property 37: Isolated Vehicle Failure Handling**
    - **Validates: Requirements 12.5**

  - [x] 11.5 Write integration tests for error scenarios
    - Test network outage recovery
    - Test authentication failure handling
    - Test partial failure scenarios
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5_

- [x] 12. Checkpoint - Verify backend services complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 13. Implement Vaadin UI components
  - [x] 13.1 Create RealmVehicleTree component
    - Implement hierarchical tree using Vaadin TreeGrid component
    - Display realms as parent nodes and vehicles as child nodes
    - Implement vehicle selection capability (checkboxes or selection mode)
    - Implement realm selection with cascading vehicle selection
    - Bind tree to realm/vehicle data from DiscoveryService
    - _Requirements: 3.2, 5.1, 5.2, 5.3, 10.2_

  - [x] 13.2 Create ConsumptionControlPanel component
    - Implement "Start Random Mode" and "Stop Random Mode" buttons
    - Implement "Start Controlled Mode" and "Stop Controlled Mode" buttons
    - Connect buttons to ConsumptionOrchestrator service methods
    - Display current mode status (Random/Controlled/Idle)
    - _Requirements: 4.5, 4.6, 10.3, 10.4_

  - [x] 13.3 Create LocationDataGrid component
    - Implement grid using Vaadin Grid component to display active vehicles
    - Add columns: Vehicle ID, Vehicle Name, Realm, Latitude, Longitude, Timestamp, Status
    - Enable Vaadin Push for real-time updates without page refresh
    - Register as listener with LocationDataHandler to receive updates
    - Update grid data within 500ms of receiving location data
    - _Requirements: 6.3, 8.1, 8.2, 8.3, 8.4, 8.5, 10.5_

  - [x] 13.4 Create MetricsPanel component
    - Display total active connections count
    - Display updates per second throughput metric
    - Display active vehicles count
    - Display active realms count
    - Display per-client metrics when multi-client mode is active
    - Update metrics every 1 second using data from MetricsCollector
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

  - [x] 13.5 Create MultiClientConfigPanel component
    - Implement input field for number of simulated clients
    - Implement "Configure" button to apply multi-client settings
    - Connect to ConsumptionOrchestrator.configureMultiClient method
    - Display current client count
    - Add validation (minimum 1, maximum 100 clients)
    - _Requirements: 7.1, 10.6_

  - [x] 13.6 Write property test for realm display completeness
    - **Property 4: Realm Display Completeness**
    - **Validates: Requirements 2.2**

  - [x] 13.7 Write property test for vehicle grouping
    - **Property 7: Vehicle Grouping by Realm**
    - **Validates: Requirements 3.2**

  - [x] 13.8 Write property test for consumption status display
    - **Property 14: Consumption Status Display**
    - **Validates: Requirements 5.7**

  - [x] 13.9 Write property test for active vehicle display
    - **Property 22: Active Vehicle Display**
    - **Validates: Requirements 8.1**

  - [x] 13.10 Write property test for latest coordinate display
    - **Property 23: Latest Coordinate Display**
    - **Validates: Requirements 8.2**

  - [x] 13.11 Write property test for timestamp display
    - **Property 24: Timestamp Display**
    - **Validates: Requirements 8.3**

  - [x] 13.12 Write property test for realm association display
    - **Property 25: Realm Association Display**
    - **Validates: Requirements 8.4**

  - [x] 13.13 Write property test for metadata display
    - **Property 26: Metadata Field Display**
    - **Validates: Requirements 8.5**

  - [x] 13.14 Write property test for hierarchical display
    - **Property 32: Hierarchical Realm-Vehicle Display**
    - **Validates: Requirements 10.2**

  - [x] 13.15 Write property test for real-time UI updates
    - **Property 33: Real-Time UI Updates**
    - **Validates: Requirements 10.5**

  - [x] 13.16 Write unit tests for UI components
    - Test component initialization
    - Test button click handlers
    - Test data binding
    - _Requirements: 10.2, 10.3, 10.4, 10.5, 10.6_

- [x] 14. Create main application view
  - [x] 14.1 Create MainView class
    - Annotate with @Route for Vaadin routing
    - Use VerticalLayout as main layout container
    - Add RealmVehicleTree component
    - Add ConsumptionControlPanel component
    - Add LocationDataGrid component
    - Add MetricsPanel component
    - Add MultiClientConfigPanel component
    - Arrange components in logical layout (controls top, tree left, grid center, metrics bottom)
    - Inject all required services (AuthenticationService, DiscoveryService, ConsumptionOrchestrator)
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

  - [x] 14.2 Implement view lifecycle and initialization
    - Call AuthenticationService.authenticate on view initialization
    - Start DiscoveryService periodic refresh after successful authentication
    - Handle authentication failures with user notification
    - Configure Vaadin Push annotation for real-time updates
    - _Requirements: 1.1, 1.3, 2.1, 2.4, 3.4_

  - [x] 14.3 Write integration tests for main view
    - Test view initialization flow
    - Test service integration
    - Test UI component interaction
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

- [x] 15. Checkpoint - Verify UI layer complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 16. Implement application entry point and configuration
  - [x] 16.1 Create Spring Boot application class
    - Create main class annotated with @SpringBootApplication
    - Configure component scanning for all packages
    - Configure Spring Boot properties for Vaadin
    - Enable async execution support with @EnableAsync
    - Enable scheduling support with @EnableScheduling
    - _Requirements: 11.1_

  - [x] 16.2 Configure application.properties
    - Set all Open Remote API configuration properties
    - Set refresh intervals (realms: 60s, vehicles: 60s, metrics: 1s)
    - Set connection timeouts (timeout: 5000ms, establishment: 2000ms)
    - Set retry configuration (max attempts: 3, initial delay: 1s, max delay: 30s)
    - Set multi-client defaults (default: 1, max: 100)
    - Configure logging levels
    - _Requirements: 13.1, 13.2, 13.3_

  - [x] 16.3 Write integration tests for application startup
    - Test application context loads successfully
    - Test all beans are created
    - Test configuration is loaded correctly
    - _Requirements: 11.1, 13.1, 13.2, 13.3_

- [x] 17. Docker containerization
  - [x] 17.1 Create multi-stage Dockerfile
    - Create Dockerfile with build stage using Maven image
    - Add runtime stage using JRE image for minimal container size
    - Configure EXPOSE directive for port 8080
    - Set appropriate working directory and entry point
    - Optimize layer caching for dependencies and application code
    - _Requirements: 14.1, 14.2_

  - [x] 17.2 Configure Spring Boot for environment variables
    - Update application.properties to support environment variable overrides
    - Add default values for all configuration properties
    - Document all configurable environment variables
    - Ensure secure handling of sensitive credentials (token, username)
    - _Requirements: 14.3, 14.4_

  - [x] 17.3 Create docker-compose.yml configuration
    - Define service configuration for the application
    - Configure environment variables for Open Remote API endpoint, credentials, and refresh intervals
    - Configure port mapping (host:8080 → container:8080)
    - Set restart policy to "unless-stopped"
    - Add health check configuration using Spring Boot Actuator
    - _Requirements: 14.5, 14.6, 14.7_

  - [x] 17.4 Configure graceful shutdown handling
    - Configure Spring Boot graceful shutdown with 30-second timeout
    - Implement SIGTERM signal handler to close active WebSocket connections
    - Ensure all threads are properly terminated on shutdown
    - Test shutdown behavior in Docker environment
    - _Requirements: 14.8_

  - [x] 17.5 Add Spring Boot Actuator for health checks
    - Add Spring Boot Actuator dependency to Maven POM
    - Configure /actuator/health endpoint
    - Implement custom health indicator for WebSocket connection status
    - Implement custom health indicator for authentication status
    - Configure Docker health check to use actuator endpoint
    - _Requirements: 14.7_

  - [x] 17.6 Write property tests for Docker-specific correctness properties
    - **Property 42: Container Build Success**
    - **Validates: Requirements 14.1**
    - **Property 43: Environment Variable Configuration**
    - **Validates: Requirements 14.3**
    - **Property 44: Application Startup in Container**
    - **Validates: Requirements 14.4**
    - **Property 45: Port Exposure and Accessibility**
    - **Validates: Requirements 14.5**
    - **Property 46: Container Orchestration Launch**
    - **Validates: Requirements 14.6**
    - **Property 47: Health Check Response**
    - **Validates: Requirements 14.7**

  - [x] 17.7 Write integration tests for Docker deployment
    - Test Docker image build process
    - Test container startup with default configuration
    - Test container startup with environment variable overrides
    - Test health endpoint accessibility
    - Test graceful shutdown behavior
    - Test port accessibility and Vaadin UI loading
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 14.8_

- [x] 18. Checkpoint - Verify Docker containerization complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 19. End-to-end integration and wiring
  - [x] 19.1 Wire all components together
    - Verify AuthenticationService → OpenRemoteRestClient integration
    - Verify DiscoveryService → AuthenticationService → OpenRemoteRestClient integration
    - Verify ConsumptionOrchestrator → WebSocketClientPool → WebSocketConnection integration
    - Verify MetricsCollector integration with all consumption flows
    - Verify LocationDataHandler → UI updates integration
    - Verify MainView → all services integration
    - _Requirements: All requirements_

  - [x] 19.2 Write end-to-end integration tests
    - Test complete authentication → discovery → consumption flow
    - Test Random Mode end-to-end
    - Test Controlled Mode end-to-end
    - Test multi-client mode end-to-end
    - Test error recovery scenarios
    - _Requirements: All requirements_

- [x] 20. Final checkpoint - Complete implementation verification
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP delivery
- Property tests validate universal correctness properties defined in the design
- Unit tests validate specific examples, edge cases, and timing requirements
- Integration tests validate component interactions and end-to-end flows
- Implementation follows bottom-up approach: foundation → services → UI → integration
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at major milestones
- All timing constraints (2s connection, 1s closure, 500ms display) are tested via unit tests
- Error handling and resilience is implemented throughout all layers
- Java concurrency primitives (CompletableFuture, ScheduledExecutorService, ConcurrentHashMap, Atomic types) are used appropriately
- Vaadin Push enables real-time UI updates without polling

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "2.2", "2.3", "2.4", "2.5", "3.1"] },
    { "id": 2, "tasks": ["3.2", "4.1"] },
    { "id": 3, "tasks": ["4.2", "4.3", "4.4", "6.1"] },
    { "id": 4, "tasks": ["6.2", "6.3", "6.4", "6.5", "6.6", "7.1"] },
    { "id": 5, "tasks": ["7.2"] },
    { "id": 6, "tasks": ["7.3", "7.4", "7.5", "7.6", "7.7", "8.1"] },
    { "id": 7, "tasks": ["8.2", "8.3", "8.4", "8.5", "8.6", "8.7", "10.1"] },
    { "id": 8, "tasks": ["10.2", "10.3", "10.4", "10.5", "10.6", "10.7", "10.8", "10.9", "10.10", "10.11", "11.1"] },
    { "id": 9, "tasks": ["11.2", "11.3", "11.4", "11.5", "13.1", "13.2"] },
    { "id": 10, "tasks": ["13.3", "13.4", "13.5", "13.6", "13.7", "13.8", "13.9", "13.10", "13.11", "13.12", "13.13", "13.14", "13.15", "13.16"] },
    { "id": 11, "tasks": ["14.1"] },
    { "id": 12, "tasks": ["14.2", "14.3", "16.1"] },
    { "id": 13, "tasks": ["16.2", "16.3"] },
    { "id": 14, "tasks": ["17.1", "17.2"] },
    { "id": 15, "tasks": ["17.3", "17.4", "17.5"] },
    { "id": 16, "tasks": ["17.6", "17.7", "19.1"] },
    { "id": 17, "tasks": ["19.2"] }
  ]
}
```
