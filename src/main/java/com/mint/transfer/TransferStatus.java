package com.mint.transfer;

/**
 * Status lifecycle of a transfer.
 * PENDING: Initiated, undergoing balance checks & lock acquisitions.
 * COMPLETED: Debit, credit, and double-entry ledger lines committed successfully.
 * FAILED: Terminated due to insufficient balance, currency mismatch, or constraint violation.
 */
public enum TransferStatus {
    PENDING,
    COMPLETED,
    FAILED
}
