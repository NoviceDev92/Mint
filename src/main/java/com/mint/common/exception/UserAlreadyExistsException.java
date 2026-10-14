package com.mint.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when trying to register with an email that is already registered.
 * Returns HTTP 409 Conflict.
 */
public class UserAlreadyExistsException extends ApiException {

    public UserAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT, "USER_ALREADY_EXISTS");
    }
}
