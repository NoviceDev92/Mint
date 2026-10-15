package com.mint.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated user attempts to access or transfer from an account they do not own.
 */
public class UnauthorizedAccountAccessException extends ApiException {

    public UnauthorizedAccountAccessException(String message) {
        super(message, HttpStatus.FORBIDDEN, "FORBIDDEN_ACCOUNT_ACCESS");
    }
}
