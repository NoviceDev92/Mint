package com.mint.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the source and destination account IDs of a transfer are identical.
 */
public class SelfTransferNotAllowedException extends ApiException {

    public SelfTransferNotAllowedException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "SELF_TRANSFER_NOT_ALLOWED");
    }
}
