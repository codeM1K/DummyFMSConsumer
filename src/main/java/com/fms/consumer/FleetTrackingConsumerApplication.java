package com.fms.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Fleet Tracking Data Consumer application.
 * Enables async processing and scheduling for background tasks such as
 * periodic discovery refresh and metrics collection.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.fms.consumer.config")
@EnableAsync
@EnableScheduling
public class FleetTrackingConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FleetTrackingConsumerApplication.class, args);
    }
}
