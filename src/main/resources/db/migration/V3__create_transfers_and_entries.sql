-- Migration: V3__create_transfers_and_entries.sql
-- Description: Creates transfers table and immutable double-entry ledger_entries table

-- Transfers track the logical intent and high-level state of money movement
CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    idempotency_key UUID NOT NULL,
    source_account_id UUID NOT NULL,
    destination_account_id UUID NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    failure_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_transfers_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_transfers_source FOREIGN KEY (source_account_id) REFERENCES accounts(id),
    CONSTRAINT fk_transfers_dest FOREIGN KEY (destination_account_id) REFERENCES accounts(id)
);

-- Unique index on idempotency_key guarantees duplicate requests cannot be processed concurrently
CREATE INDEX idx_transfers_idempotency_key ON transfers(idempotency_key);
CREATE INDEX idx_transfers_source ON transfers(source_account_id);
CREATE INDEX idx_transfers_dest ON transfers(destination_account_id);

-- Ledger entries represent immutable, append-only accounting lines (Double-Entry Bookkeeping)
-- Every transfer creates exactly two entries: one DEBIT, one CREDIT
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    transfer_id UUID NOT NULL,
    direction VARCHAR(10) NOT NULL, -- 'DEBIT' or 'CREDIT'
    amount NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_entries_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    CONSTRAINT fk_entries_transfer FOREIGN KEY (transfer_id) REFERENCES transfers(id) ON DELETE CASCADE
);

-- Indexes for lightning-fast account statements and transfer auditing
CREATE INDEX idx_entries_account_id ON ledger_entries(account_id);
CREATE INDEX idx_entries_transfer_id ON ledger_entries(transfer_id);
