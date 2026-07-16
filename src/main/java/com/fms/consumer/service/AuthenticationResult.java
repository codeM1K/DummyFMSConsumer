package com.fms.consumer.service;

/**
 * Represents the result of an authentication attempt.
 */
public class AuthenticationResult {

    private final boolean success;
    private final String sessionToken;
    private final String errorMessage;

    private AuthenticationResult(boolean success, String sessionToken, String errorMessage) {
        this.success = success;
        this.sessionToken = sessionToken;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a successful authentication result with the given session token.
     */
    public static AuthenticationResult success(String sessionToken) {
        return new AuthenticationResult(true, sessionToken, null);
    }

    /**
     * Creates a failed authentication result with the given error message.
     */
    public static AuthenticationResult failure(String errorMessage) {
        return new AuthenticationResult(false, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "AuthenticationResult{" +
                "success=" + success +
                ", sessionToken='" + (sessionToken != null ? "***" : "null") + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
