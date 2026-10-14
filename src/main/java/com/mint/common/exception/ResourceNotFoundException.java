package com.mint.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource (user, account, etc.) cannot be found.
 * Returns HTTP 404 Not Found.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }
}
