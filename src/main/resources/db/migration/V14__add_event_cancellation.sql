ALTER TABLE events
    ADD COLUMN cancelled_at        TIMESTAMPTZ,
    ADD COLUMN cancellation_reason VARCHAR(500);

ALTER TABLE events
    ADD CONSTRAINT ck_events_cancellation_reason
        CHECK (cancellation_reason IS NULL OR cancelled_at IS NOT NULL);

CREATE INDEX idx_events_cancelled_at ON events (cancelled_at) WHERE cancelled_at IS NOT NULL;

ALTER TABLE payments
    ADD COLUMN refunded_at      TIMESTAMPTZ,
    ADD COLUMN refund_reference VARCHAR(100);

ALTER TABLE payments DROP CONSTRAINT ck_payments_status;

ALTER TABLE payments
    ADD CONSTRAINT ck_payments_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REFUNDED'));

ALTER TABLE payments
    ADD CONSTRAINT ck_payments_refunded_at
        CHECK ((status = 'REFUNDED') = (refunded_at IS NOT NULL));
