package com.mint.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for custom business errors in the application.
 * Holds the HTTP status and a distinct error code string.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
