-- Test fixture seed data for Balance Enquiry integration tests
-- Run against a test PostgreSQL database before executing tests/integration/

CREATE TABLE IF NOT EXISTS accounts (
    account_id        VARCHAR(64)     PRIMARY KEY,
    owner_subject     VARCHAR(255)    NOT NULL,
    available_balance NUMERIC(19, 4)  NOT NULL,
    currency          CHAR(3)         NOT NULL,
    status            VARCHAR(16)     NOT NULL DEFAULT 'active',
    last_updated_at   TIMESTAMPTZ     NULL,
    CONSTRAINT accounts_status_check CHECK (status IN ('active', 'suspended'))
);

CREATE INDEX IF NOT EXISTS idx_accounts_owner_subject ON accounts (owner_subject);

-- Active account owned by customer-sub-abc
INSERT INTO accounts (account_id, owner_subject, available_balance, currency, status, last_updated_at)
VALUES ('acc_test_001', 'customer-sub-abc', 1024.5000, 'GBP', 'active', NOW())
ON CONFLICT (account_id) DO UPDATE
    SET available_balance = EXCLUDED.available_balance,
        owner_subject     = EXCLUDED.owner_subject,
        status            = EXCLUDED.status,
        last_updated_at   = EXCLUDED.last_updated_at;

-- Suspended account owned by customer-sub-xyz
INSERT INTO accounts (account_id, owner_subject, available_balance, currency, status, last_updated_at)
VALUES ('acc_test_002', 'customer-sub-xyz', 500.0000, 'USD', 'suspended', NOW())
ON CONFLICT (account_id) DO UPDATE
    SET available_balance = EXCLUDED.available_balance,
        owner_subject     = EXCLUDED.owner_subject,
        status            = EXCLUDED.status,
        last_updated_at   = EXCLUDED.last_updated_at;
