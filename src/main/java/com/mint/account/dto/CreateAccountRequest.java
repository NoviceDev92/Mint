package com.mint.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * Request payload for creating a new account / wallet.
 */
public class CreateAccountRequest {

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^(INR|USD|EUR|GBP)$", message = "Currency must be one of INR, USD, EUR, GBP")
    private String currency;

    @DecimalMin(value = "0.00", message = "Initial deposit cannot be negative")
    private BigDecimal initialDeposit;

    public CreateAccountRequest() {
    }

    public CreateAccountRequest(String currency, BigDecimal initialDeposit) {
        this.currency = currency;
        this.initialDeposit = initialDeposit;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getInitialDeposit() {
        return initialDeposit;
    }

    public void setInitialDeposit(BigDecimal initialDeposit) {
        this.initialDeposit = initialDeposit;
    }
}
