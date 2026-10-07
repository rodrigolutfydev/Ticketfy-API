CREATE TABLE payout_accounts (
    organizer_id   UUID         PRIMARY KEY,
    document_type  VARCHAR(4)   NOT NULL,
    document       TEXT         NOT NULL,
    holder_name    VARCHAR(150) NOT NULL,
    pix_key_type   VARCHAR(10)  NOT NULL,
    pix_key        TEXT         NOT NULL,
    key_changed_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ,
    version        BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_payout_accounts_organizer
        FOREIGN KEY (organizer_id) REFERENCES users (id),

    CONSTRAINT ck_payout_accounts_document_type
        CHECK (document_type IN ('CPF', 'CNPJ')),

    CONSTRAINT ck_payout_accounts_pix_key_type
        CHECK (pix_key_type IN ('CPF', 'CNPJ', 'EMAIL', 'PHONE', 'RANDOM'))
);

CREATE TABLE payouts (
    id                    UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id          UUID           NOT NULL,
    amount                NUMERIC(10, 2) NOT NULL,
    status                VARCHAR(20)    NOT NULL,
    idempotency_key       VARCHAR(100),
    document_type         VARCHAR(4)     NOT NULL,
    document              TEXT           NOT NULL,
    holder_name           VARCHAR(150)   NOT NULL,
    pix_key_type          VARCHAR(10)    NOT NULL,
    pix_key               TEXT           NOT NULL,
    transfer_reference    VARCHAR(100),
    failure_reason        VARCHAR(255),
    requested_at          TIMESTAMPTZ    NOT NULL,
    processing_started_at TIMESTAMPTZ,
    finished_at           TIMESTAMPTZ,
    updated_at            TIMESTAMPTZ,
    version               BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_payouts_organizer
        FOREIGN KEY (organizer_id) REFERENCES users (id),

    CONSTRAINT uk_payouts_organizer_idempotency_key
        UNIQUE (organizer_id, idempotency_key),

    CONSTRAINT ck_payouts_amount
        CHECK (amount > 0),

    CONSTRAINT ck_payouts_status
        CHECK (status IN ('REQUESTED', 'PROCESSING', 'PAID', 'FAILED', 'CANCELLED')),

    CONSTRAINT ck_payouts_document_type
        CHECK (document_type IN ('CPF', 'CNPJ')),

    CONSTRAINT ck_payouts_pix_key_type
        CHECK (pix_key_type IN ('CPF', 'CNPJ', 'EMAIL', 'PHONE', 'RANDOM')),

    CONSTRAINT ck_payouts_finished_at
        CHECK ((status IN ('PAID', 'FAILED', 'CANCELLED')) = (finished_at IS NOT NULL))
);

CREATE UNIQUE INDEX uk_payouts_organizer_in_progress
    ON payouts (organizer_id)
    WHERE status IN ('REQUESTED', 'PROCESSING');

CREATE INDEX idx_payouts_organizer_requested ON payouts (organizer_id, requested_at DESC);

CREATE INDEX idx_payouts_in_progress ON payouts (status, requested_at)
    WHERE status IN ('REQUESTED', 'PROCESSING');

ALTER TABLE organizer_ledger_entries
    ADD COLUMN payout_id UUID;

ALTER TABLE organizer_ledger_entries
    ALTER COLUMN event_id DROP NOT NULL,
    ALTER COLUMN order_id DROP NOT NULL;

ALTER TABLE organizer_ledger_entries
    ADD CONSTRAINT fk_ledger_entries_payout
        FOREIGN KEY (payout_id) REFERENCES payouts (id);

ALTER TABLE organizer_ledger_entries
    ADD CONSTRAINT uk_ledger_entries_payout_type
        UNIQUE (payout_id, type);

ALTER TABLE organizer_ledger_entries DROP CONSTRAINT ck_ledger_entries_type;

ALTER TABLE organizer_ledger_entries
    ADD CONSTRAINT ck_ledger_entries_type
        CHECK (type IN ('SALE_CREDIT', 'REFUND_DEBIT', 'PAYOUT_DEBIT', 'PAYOUT_REVERSAL'));

ALTER TABLE organizer_ledger_entries DROP CONSTRAINT ck_ledger_entries_amount;

ALTER TABLE organizer_ledger_entries
    ADD CONSTRAINT ck_ledger_entries_amount
        CHECK ((type IN ('SALE_CREDIT', 'PAYOUT_REVERSAL') AND amount > 0)
            OR (type IN ('REFUND_DEBIT', 'PAYOUT_DEBIT') AND amount < 0));

ALTER TABLE organizer_ledger_entries
    ADD CONSTRAINT ck_ledger_entries_reference
        CHECK ((type IN ('SALE_CREDIT', 'REFUND_DEBIT')
                    AND order_id IS NOT NULL AND event_id IS NOT NULL AND payout_id IS NULL)
            OR (type IN ('PAYOUT_DEBIT', 'PAYOUT_REVERSAL')
                    AND payout_id IS NOT NULL AND order_id IS NULL AND event_id IS NULL));

CREATE INDEX idx_ledger_entries_payout ON organizer_ledger_entries (payout_id) WHERE payout_id IS NOT NULL;
