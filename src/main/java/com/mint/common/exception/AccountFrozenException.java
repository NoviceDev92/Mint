package com.mint.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an account is administratively frozen and cannot participate in transfers.
 */
public class AccountFrozenException extends ApiException {

    public AccountFrozenException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "ACCOUNT_FROZEN");
    }
}
