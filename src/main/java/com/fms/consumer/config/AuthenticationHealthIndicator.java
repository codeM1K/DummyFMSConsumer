package com.fms.consumer.config;

import com.fms.consumer.service.AuthenticationService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for authentication status.
 * Reports UP when authentication is successful and the session token is valid.
 * Reports DOWN when not authenticated.
 */
@Component
public class AuthenticationHealthIndicator implements HealthIndicator {

    private final AuthenticationService authenticationService;

    public AuthenticationHealthIndicator(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Override
    public Health health() {
        if (authenticationService.isAuthenticated()) {
            return Health.up()
                    .withDetail("status", "authenticated")
                    .build();
        } else {
            return Health.down()
                    .withDetail("status", "not authenticated")
                    .build();
        }
    }
}
