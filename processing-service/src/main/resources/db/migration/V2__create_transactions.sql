-- V2: Create transactions table
-- Flyway owns this schema. JPA is set to ddl-auto: validate.
CREATE TABLE transactions (
    id BIGSERIAL NOT NULL,
    source_id VARCHAR(255) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    account_id VARCHAR(128) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    merchant_name VARCHAR(255) NOT NULL,
    merchant_mcc VARCHAR(4),
    transacted_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL,
    category_id BIGINT,
    external_id UUID NOT NULL DEFAULT gen_random_uuid(),
    raw_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE transactions ADD CONSTRAINT pk_transactions PRIMARY KEY(id);
ALTER TABLE transactions ADD CONSTRAINT uq_transactions_source UNIQUE(source_id);
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_status CHECK(status IN('PENDING','SETTLED','REVERSED'));
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_source CHECK(source_type IN('BANK_FEED','PAYMENT_PROCESSOR','CARD_NETWORK'));
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_amount CHECK(amount>0);

CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_transacted_at ON transactions(transacted_at DESC);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_category_id ON transactions(category_id);
CREATE INDEX idx_transactions_external_id ON transactions(external_id);
CREATE INDEX idx_tx_raw_payload ON transactions USING gin(raw_payload);
CREATE INDEX idx_tx_account_date ON transactions(account_id,transacted_at DESC);

ALTER TABLE transactions ADD CONSTRAINT fk_transactions_category FOREIGN KEY(category_id) REFERENCES categories(id);
