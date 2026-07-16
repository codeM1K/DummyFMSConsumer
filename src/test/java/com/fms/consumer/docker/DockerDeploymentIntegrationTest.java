package com.fms.consumer.docker;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying Docker deployment artifacts are correct and consistent.
 * Tests validate file structure, configuration consistency between Dockerfile and
 * docker-compose.yml, and that the application supports the required Docker deployment model.
 *
 * Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 14.8
 */
class DockerDeploymentIntegrationTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

    @Test
    void dockerfileExists() {
        assertTrue(Files.exists(PROJECT_ROOT.resolve("Dockerfile")),
                "Dockerfile must exist in project root");
    }

    @Test
    void dockerComposeExists() {
        assertTrue(Files.exists(PROJECT_ROOT.resolve("docker-compose.yml")),
                "docker-compose.yml must exist in project root");
    }

    @Test
    void dockerfileUsesMultiStageBuild() throws IOException {
        String content = Files.readString(PROJECT_ROOT.resolve("Dockerfile"));
        long fromCount = content.lines()
                .filter(line -> line.trim().startsWith("FROM"))
                .count();
        assertTrue(fromCount >= 2, "Dockerfile should use multi-stage build (at least 2 FROM statements)");
    }

    @Test
    void dockerfileUsesJavaBaseImage() throws IOException {
        String content = Files.readString(PROJECT_ROOT.resolve("Dockerfile"));
        assertTrue(content.contains("eclipse-temurin") || content.contains("openjdk"),
                "Dockerfile must use eclipse-temurin or openjdk base image");
    }

    @Test
    void dockerfileExposesDefaultPort() throws IOException {
        String content = Files.readString(PROJECT_ROOT.resolve("Dockerfile"));
        assertTrue(content.contains("EXPOSE 8080"),
                "Dockerfile must expose port 8080");
    }

    @Test
    void dockerfileHasHealthCheck() throws IOException {
        String content = Files.readString(PROJECT_ROOT.resolve("Dockerfile"));
        assertTrue(content.contains("HEALTHCHECK"),
                "Dockerfile must have a HEALTHCHECK instruction");
        assertTrue(content.contains("/actuator/health"),
                "Healthcheck must use Spring Boot Actuator endpoint");
    }

    @Test
    void dockerfileConfiguresEnvironmentVariables() throws IOException {
        String content = Files.readString(PROJECT_ROOT.resolve("Dockerfile"));

        List<String> requiredEnvVars = List.of(
                "SERVER_PORT",
                "OPENREMOTE_API_ENDPOINT",
                "OPENREMOTE_API_CLIENT_ID",
                "OPENREMOTE_API_CLIENT_SECRET",
                "OPENREMOTE_REFRESH_REALMS",
                "OPENREMOTE_REFRESH_VEHICLES",
                "OPENREMOTE_CONNECTION_TIMEOUT",
                "OPENREMOTE_RETRY_MAX_ATTEMPTS"
        );

        for (String envVar : requiredEnvVars) {
            assertTrue(content.contains(envVar),
                    "Dockerfile must define environment variable: " + envVar);
        }
    }

    @Test
    void dockerComposeHasRestartPolicy() throws IOException {
        String content = Files.readString(PROJECT_ROOT.resolve("docker-compose.yml"));
        assertTrue(content.contains("unless-stopped"),
                "docker-compose must have restart: unless-stopped");
    }

    @Test
    void dockerComposeHasPortMapping() throws IOException {
        String content = Files.readString(PROJECT_ROOT.resolve("docker-compose.yml"));
        assertTrue(content.contains("9000:8080") || content.contains("8080:8080"),
                "docker-compose must map container port 8080");
    }

    @Test
    void dockerComposeHasHealthCheck() throws IOException {
        String content = Files.readString(PROJECT_ROOT.resolve("docker-compose.yml"));
        assertTrue(content.contains("healthcheck"),
                "docker-compose must have healthcheck section");
        assertTrue(content.contains("/actuator/health"),
                "docker-compose healthcheck must use /actuator/health");
    }

    @Test
    void dockerComposeHasEnvironmentSection() throws IOException {
        String content = Files.readString(PROJECT_ROOT.resolve("docker-compose.yml"));
        assertTrue(content.contains("environment"),
                "docker-compose must have environment section");
        assertTrue(content.contains("OPENREMOTE_API_ENDPOINT"),
                "docker-compose must configure API endpoint");
    }

    @Test
    void applicationPropertiesSupportsGracefulShutdown() throws IOException {
        String content = Files.readString(
                PROJECT_ROOT.resolve("src/main/resources/application.properties"));
        assertTrue(content.contains("server.shutdown=graceful"),
                "application.properties must enable graceful shutdown");
        assertTrue(content.contains("spring.lifecycle.timeout-per-shutdown-phase"),
                "application.properties must configure shutdown timeout");
    }

    @Test
    void applicationPropertiesExposesActuatorHealth() throws IOException {
        String content = Files.readString(
                PROJECT_ROOT.resolve("src/main/resources/application.properties"));
        assertTrue(content.contains("management.endpoints.web.exposure.include"),
                "Actuator endpoints must be exposed");
        assertTrue(content.contains("health"),
                "Health endpoint must be exposed");
    }
}
