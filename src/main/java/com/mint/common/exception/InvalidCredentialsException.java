package com.mint.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when login fails due to incorrect email or password.
 * Returns HTTP 401 Unauthorized.
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
    }
}
