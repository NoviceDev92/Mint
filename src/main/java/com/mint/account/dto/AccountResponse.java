package com.mint.account.dto;

import com.mint.account.Account;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing an account.
 */
public class AccountResponse {

    private UUID id;
    private UUID userId;
    private String ownerName;
    private String currency;
    private BigDecimal balance;
    private boolean frozen;
    private Instant createdAt;

    public AccountResponse() {
    }

    public AccountResponse(UUID id, UUID userId, String ownerName, String currency,
                           BigDecimal balance, boolean frozen, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.ownerName = ownerName;
        this.currency = currency;
        this.balance = balance;
        this.frozen = frozen;
        this.createdAt = createdAt;
    }

    public static AccountResponse fromEntity(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getUserId(),
                account.getOwnerName(),
                account.getCurrency(),
                account.getBalance(),
                account.isFrozen(),
                account.getCreatedAt()
        );
    }

    // --- Getters & Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
