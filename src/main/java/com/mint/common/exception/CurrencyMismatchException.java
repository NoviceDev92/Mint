package com.mint.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when attempting a transfer between accounts with different currencies (e.g. INR to USD).
 */
public class CurrencyMismatchException extends ApiException {

    public CurrencyMismatchException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "CURRENCY_MISMATCH");
    }
}
