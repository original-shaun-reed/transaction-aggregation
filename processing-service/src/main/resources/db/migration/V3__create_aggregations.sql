-- V3: Create aggregations table

CREATE TABLE aggregations (
    id BIGSERIAL NOT NULL,
    account_id VARCHAR(128) NOT NULL,
    category_id BIGINT NOT NULL,
    period_date DATE NOT NULL,
    period_type VARCHAR(8) NOT NULL,
    total_spend NUMERIC(19,4) NOT NULL DEFAULT 0,
    transaction_count BIGINT NOT NULL DEFAULT 0,
    total_reversed NUMERIC(19,4) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE aggregations ADD CONSTRAINT pk_aggregations PRIMARY KEY(id);
ALTER TABLE aggregations ADD CONSTRAINT uq_agg_account_category_period UNIQUE(account_id,category_id,period_date,period_type);
ALTER TABLE aggregations ADD CONSTRAINT fk_aggregations_category FOREIGN KEY(category_id) REFERENCES categories(id);
ALTER TABLE aggregations ADD CONSTRAINT ck_aggregations_period_type CHECK(period_type IN('DAILY','WEEKLY','MONTHLY'));
ALTER TABLE aggregations ADD CONSTRAINT ck_aggregations_spend CHECK(total_spend>=0);
ALTER TABLE aggregations ADD CONSTRAINT ck_aggregations_count CHECK(transaction_count>=0);
ALTER TABLE aggregations ADD CONSTRAINT ck_aggregations_reversed CHECK(total_reversed>=0);

CREATE INDEX idx_aggregations_account_period ON aggregations(account_id,period_date DESC);
CREATE INDEX idx_aggregations_category_period ON aggregations(category_id,period_date DESC);
CREATE INDEX idx_aggregations_period_type_date ON aggregations(period_type,period_date DESC);
CREATE INDEX idx_aggregations_account_type_period ON aggregations(account_id,period_type,period_date DESC);
CREATE INDEX idx_aggregations_account_category_type ON aggregations(account_id,category_id,period_type);
