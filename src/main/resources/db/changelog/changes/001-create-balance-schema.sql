--liquibase formatted sql

--changeset balance-challenge:001

CREATE TABLE accounts (
    id VARCHAR(100) PRIMARY KEY,
    balance BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_accounts_non_negative_balance CHECK (balance >= 0)
);

CREATE TABLE balance_operation (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(100) NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    source_account_id VARCHAR(100),
    destination_account_id VARCHAR(100),
    amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_code VARCHAR(50),
    source_balance_after BIGINT,
    destination_balance_after BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uk_balance_operation_transaction_id UNIQUE (transaction_id),
    CONSTRAINT ck_balance_operation_positive_amount CHECK (amount > 0),
    CONSTRAINT ck_balance_operation_type CHECK (operation_type IN ('CREDIT', 'DEBIT', 'TRANSFER')),
    CONSTRAINT ck_balance_operation_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'REJECTED')),
    CONSTRAINT ck_balance_operation_accounts CHECK (
        (operation_type = 'CREDIT' AND source_account_id IS NULL AND destination_account_id IS NOT NULL)
        OR (operation_type = 'DEBIT' AND source_account_id IS NOT NULL AND destination_account_id IS NULL)
        OR (operation_type = 'TRANSFER' AND source_account_id IS NOT NULL
            AND destination_account_id IS NOT NULL AND source_account_id <> destination_account_id)
    )
);
