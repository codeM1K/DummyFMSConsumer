package com.fms.consumer.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO representing the authentication response from the Open Remote API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthResponse {

    private String sessionToken;
    private boolean success;
    private String errorMessage;

    public AuthResponse() {
    }

    public AuthResponse(String sessionToken, boolean success, String errorMessage) {
        this.sessionToken = sessionToken;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "AuthResponse{" +
                "sessionToken='" + (sessionToken != null ? "***" : "null") + '\'' +
                ", success=" + success +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
