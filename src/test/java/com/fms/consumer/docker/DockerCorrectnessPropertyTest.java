package com.fms.consumer.docker;

import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property tests for Docker configuration correctness.
 * Validates Dockerfile structure, environment variable support,
 * and docker-compose configuration.
 *
 * **Validates: Requirements 14.1, 14.3, 14.4, 14.5, 14.6, 14.7**
 */
class DockerCorrectnessPropertyTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

    /**
     * Property 42: Container Build Success
     * Verifies Dockerfile exists and contains required build stages.
     * **Validates: Requirements 14.1**
     */
    @Property(tries = 1)
    void dockerfileExistsAndHasMultiStageStructure() throws IOException {
        Path dockerfile = PROJECT_ROOT.resolve("Dockerfile");
        assertTrue(Files.exists(dockerfile), "Dockerfile must exist in project root");

        String content = Files.readString(dockerfile);

        // Multi-stage build
        assertTrue(content.contains("FROM") && content.indexOf("FROM", content.indexOf("FROM") + 1) > 0,
                "Dockerfile must have at least two FROM statements (multi-stage build)");

        // Builder stage
        assertTrue(content.contains("as builder") || content.contains("AS builder"),
                "Dockerfile must have a named builder stage");

        // Java base images
        assertTrue(content.contains("eclipse-temurin") || content.contains("openjdk"),
                "Dockerfile must use a Java base image (eclipse-temurin or openjdk)");

        // JDK in build stage, JRE in runtime
        assertTrue(content.contains("jdk"), "Build stage should use JDK image");
        assertTrue(content.contains("jre"), "Runtime stage should use JRE image");
    }

    /**
     * Property 43: Environment Variable Configuration
     * Verifies that all configurable properties support environment variable overrides.
     * **Validates: Requirements 14.3**
     */
    @Property(tries = 1)
    void applicationPropertiesSupportsEnvironmentVariables() throws IOException {
        Path appProperties = PROJECT_ROOT.resolve("src/main/resources/application.properties");
        assertTrue(Files.exists(appProperties), "application.properties must exist");

        String content = Files.readString(appProperties);

        // Must support environment variable overrides using ${VAR:default} syntax
        assertTrue(content.contains("${OPENREMOTE_API_ENDPOINT:"),
                "API endpoint must support env var override");
        assertTrue(content.contains("${OPENREMOTE_API_USERNAME:"),
                "Username must support env var override");
        assertTrue(content.contains("${OPENREMOTE_API_TOKEN:"),
                "Token must support env var override");
        assertTrue(content.contains("${SERVER_PORT:"),
                "Server port must support env var override");
    }

    /**
     * Property 44: Application Startup in Container
     * Verifies the Dockerfile has a proper ENTRYPOINT for starting the Spring Boot app.
     * **Validates: Requirements 14.4**
     */
    @Property(tries = 1)
    void dockerfileHasValidEntrypoint() throws IOException {
        Path dockerfile = PROJECT_ROOT.resolve("Dockerfile");
        String content = Files.readString(dockerfile);

        assertTrue(content.contains("ENTRYPOINT") || content.contains("CMD"),
                "Dockerfile must have an ENTRYPOINT or CMD for starting the application");
        assertTrue(content.contains("app.jar"),
                "Dockerfile must reference the application jar");
    }

    /**
     * Property 45: Port Exposure and Accessibility
     * Verifies the Dockerfile exposes port 8080.
     * **Validates: Requirements 14.5**
     */
    @Property(tries = 1)
    void dockerfileExposesPort8080() throws IOException {
        Path dockerfile = PROJECT_ROOT.resolve("Dockerfile");
        String content = Files.readString(dockerfile);

        assertTrue(content.contains("EXPOSE 8080"),
                "Dockerfile must expose port 8080");
    }

    /**
     * Property 46: Container Orchestration Launch
     * Verifies docker-compose.yml exists and has correct service configuration.
     * **Validates: Requirements 14.6**
     */
    @Property(tries = 1)
    void dockerComposeHasCorrectConfiguration() throws IOException {
        Path dockerCompose = PROJECT_ROOT.resolve("docker-compose.yml");
        assertTrue(Files.exists(dockerCompose), "docker-compose.yml must exist");

        String content = Files.readString(dockerCompose);

        // Must have services section
        assertTrue(content.contains("services:"), "docker-compose must define services");

        // Port mapping
        assertTrue(content.contains("8080:8080"), "docker-compose must map port 8080");

        // Restart policy
        assertTrue(content.contains("unless-stopped"),
                "docker-compose must set restart policy to unless-stopped");

        // Environment variables
        assertTrue(content.contains("OPENREMOTE_API_ENDPOINT"),
                "docker-compose must configure API endpoint env var");
        assertTrue(content.contains("OPENREMOTE_API_TOKEN"),
                "docker-compose must configure API token env var");
    }

    /**
     * Property 47: Health Check Response
     * Verifies Docker health check is configured to use actuator endpoint.
     * **Validates: Requirements 14.7**
     */
    @Property(tries = 1)
    void dockerHealthCheckUsesActuator() throws IOException {
        Path dockerfile = PROJECT_ROOT.resolve("Dockerfile");
        String content = Files.readString(dockerfile);

        assertTrue(content.contains("HEALTHCHECK"),
                "Dockerfile must define a HEALTHCHECK");
        assertTrue(content.contains("/actuator/health"),
                "Docker HEALTHCHECK must use /actuator/health endpoint");

        // Also check docker-compose
        Path dockerCompose = PROJECT_ROOT.resolve("docker-compose.yml");
        String composeContent = Files.readString(dockerCompose);

        assertTrue(composeContent.contains("healthcheck"),
                "docker-compose must define a healthcheck");
        assertTrue(composeContent.contains("/actuator/health"),
                "docker-compose healthcheck must use /actuator/health endpoint");
    }

    /**
     * Verifies all environment variables in Dockerfile have reasonable defaults.
     */
    @Property(tries = 1)
    void dockerfileEnvironmentVariablesHaveDefaults() throws IOException {
        Path dockerfile = PROJECT_ROOT.resolve("Dockerfile");
        String content = Files.readString(dockerfile);

        // All ENV vars should have defaults set
        assertTrue(content.contains("ENV SERVER_PORT=8080"));
        assertTrue(content.contains("ENV OPENREMOTE_API_ENDPOINT="));
        assertTrue(content.contains("ENV OPENREMOTE_API_USERNAME="));
        assertTrue(content.contains("ENV OPENREMOTE_API_TOKEN="));
    }
}
