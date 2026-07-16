package com.fms.consumer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Open Remote API connection.
 * Maps properties from the "openremote" prefix in application.properties.
 */
@Component
@ConfigurationProperties(prefix = "openremote")
public class OpenRemoteProperties {

    private Api api = new Api();
    private Refresh refresh = new Refresh();
    private Connection connection = new Connection();
    private Retry retry = new Retry();
    private Simulation simulation = new Simulation();

    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api;
    }

    public Refresh getRefresh() {
        return refresh;
    }

    public void setRefresh(Refresh refresh) {
        this.refresh = refresh;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public Simulation getSimulation() {
        return simulation;
    }

    public void setSimulation(Simulation simulation) {
        this.simulation = simulation;
    }

    public static class Api {
        private String endpoint = "https://fms.pcp.com.gr";
        private String clientId = "alamanos-test";
        private String clientSecret = "hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc";

        /**
         * @deprecated Use {@link #clientId} instead. Kept for backward compatibility.
         */
        private String username;
        /**
         * @deprecated Use {@link #clientSecret} instead. Kept for backward compatibility.
         */
        private String token;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        /**
         * @deprecated Use {@link #getClientId()} instead.
         */
        @Deprecated
        public String getUsername() {
            return username != null ? username : clientId;
        }

        /**
         * @deprecated Use {@link #setClientId(String)} instead.
         */
        @Deprecated
        public void setUsername(String username) {
            this.username = username;
            if (username != null) {
                this.clientId = username;
            }
        }

        /**
         * @deprecated Use {@link #getClientSecret()} instead.
         */
        @Deprecated
        public String getToken() {
            return token != null ? token : clientSecret;
        }

        /**
         * @deprecated Use {@link #setClientSecret(String)} instead.
         */
        @Deprecated
        public void setToken(String token) {
            this.token = token;
            if (token != null) {
                this.clientSecret = token;
            }
        }
    }

    public static class Refresh {
        private int realms = 60;
        private int vehicles = 60;
        private int metrics = 1;

        public int getRealms() {
            return realms;
        }

        public void setRealms(int realms) {
            this.realms = realms;
        }

        public int getVehicles() {
            return vehicles;
        }

        public void setVehicles(int vehicles) {
            this.vehicles = vehicles;
        }

        public int getMetrics() {
            return metrics;
        }

        public void setMetrics(int metrics) {
            this.metrics = metrics;
        }
    }

    public static class Connection {
        private int timeout = 5000;
        private int establishment = 2000;

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public int getEstablishment() {
            return establishment;
        }

        public void setEstablishment(int establishment) {
            this.establishment = establishment;
        }
    }

    public static class Retry {
        private int maxAttempts = 3;
        private int initialDelay = 1000;
        private int maxDelay = 30000;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public int getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(int initialDelay) {
            this.initialDelay = initialDelay;
        }

        public int getMaxDelay() {
            return maxDelay;
        }

        public void setMaxDelay(int maxDelay) {
            this.maxDelay = maxDelay;
        }
    }

    public static class Simulation {
        private int defaultClients = 1;
        private int maxClients = 100;

        public int getDefaultClients() {
            return defaultClients;
        }

        public void setDefaultClients(int defaultClients) {
            this.defaultClients = defaultClients;
        }

        public int getMaxClients() {
            return maxClients;
        }

        public void setMaxClients(int maxClients) {
            this.maxClients = maxClients;
        }
    }
}
