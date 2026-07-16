# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom first for dependency caching
COPY pom.xml ./
RUN mvn dependency:go-offline -B || true

# Copy source code
COPY src ./src

# Build the application
RUN mvn package -Pproduction -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENV SERVER_PORT=8080
ENV OPENREMOTE_API_ENDPOINT=https://fms.pcp.com.gr
ENV OPENREMOTE_API_CLIENT_ID=
ENV OPENREMOTE_API_CLIENT_SECRET=
ENV OPENREMOTE_REFRESH_REALMS=60
ENV OPENREMOTE_REFRESH_VEHICLES=60
ENV OPENREMOTE_REFRESH_METRICS=2
ENV OPENREMOTE_CONNECTION_TIMEOUT=5000
ENV OPENREMOTE_CONNECTION_ESTABLISHMENT=2000
ENV OPENREMOTE_RETRY_MAX_ATTEMPTS=3
ENV OPENREMOTE_RETRY_INITIAL_DELAY=1000
ENV OPENREMOTE_RETRY_MAX_DELAY=30000
ENV OPENREMOTE_SIMULATION_DEFAULT_CLIENTS=1
ENV OPENREMOTE_SIMULATION_MAX_CLIENTS=100

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
