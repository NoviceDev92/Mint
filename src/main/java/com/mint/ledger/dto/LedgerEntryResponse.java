package com.mint.ledger.dto;

import com.mint.ledger.Direction;
import com.mint.ledger.LedgerEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing an immutable line in the accounting ledger.
 */
public class LedgerEntryResponse {

    private UUID id;
    private UUID accountId;
    private UUID transferId;
    private Direction direction;
    private BigDecimal amount;
    private Instant createdAt;

    public LedgerEntryResponse() {
    }

    public LedgerEntryResponse(UUID id, UUID accountId, UUID transferId,
                               Direction direction, BigDecimal amount, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.transferId = transferId;
        this.direction = direction;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public static LedgerEntryResponse fromEntity(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getAccount() != null ? entry.getAccount().getId() : null,
                entry.getTransfer() != null ? entry.getTransfer().getId() : null,
                entry.getDirection(),
                entry.getAmount(),
                entry.getCreatedAt()
        );
    }

    // --- Getters & Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public void setTransferId(UUID transferId) {
        this.transferId = transferId;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
