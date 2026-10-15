-- Migration: V2__create_accounts_table.sql
-- Description: Creates the accounts table representing user wallets/ledger accounts

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    owner_name VARCHAR(255) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    balance NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    frozen BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Index on user_id for fast retrieval of all accounts belonging to a specific user
CREATE INDEX idx_accounts_user_id ON accounts(user_id);
