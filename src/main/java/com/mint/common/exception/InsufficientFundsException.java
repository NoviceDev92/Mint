package com.mint.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an account does not have sufficient balance to complete a debit or transfer.
 */
public class InsufficientFundsException extends ApiException {

    public InsufficientFundsException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS");
    }
}
