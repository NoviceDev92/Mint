package com.mint.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a refresh token or access token is invalid, expired, or malformed.
 * Returns HTTP 401 Unauthorized.
 */
public class InvalidTokenException extends ApiException {

    public InvalidTokenException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
    }
}
