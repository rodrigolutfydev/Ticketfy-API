ALTER TABLE payouts
    ADD COLUMN rejection_reason VARCHAR(500),
    ADD COLUMN reviewed_by      UUID,
    ADD COLUMN reviewed_at      TIMESTAMPTZ;

ALTER TABLE payouts
    ADD CONSTRAINT fk_payouts_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES users (id);

ALTER TABLE payouts DROP CONSTRAINT ck_payouts_status;

ALTER TABLE payouts
    ADD CONSTRAINT ck_payouts_status
        CHECK (status IN ('UNDER_REVIEW', 'REQUESTED', 'PROCESSING', 'PAID', 'FAILED', 'CANCELLED', 'REJECTED'));

ALTER TABLE payouts DROP CONSTRAINT ck_payouts_finished_at;

ALTER TABLE payouts
    ADD CONSTRAINT ck_payouts_finished_at
        CHECK ((status IN ('PAID', 'FAILED', 'CANCELLED', 'REJECTED')) = (finished_at IS NOT NULL));

ALTER TABLE payouts
    ADD CONSTRAINT ck_payouts_rejection_reason
        CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL));

DROP INDEX uk_payouts_organizer_in_progress;

CREATE UNIQUE INDEX uk_payouts_organizer_in_progress
    ON payouts (organizer_id)
    WHERE status IN ('UNDER_REVIEW', 'REQUESTED', 'PROCESSING');

DROP INDEX idx_payouts_in_progress;

CREATE INDEX idx_payouts_in_progress ON payouts (status, requested_at)
    WHERE status IN ('UNDER_REVIEW', 'REQUESTED', 'PROCESSING');

CREATE TABLE payout_blocks (
    organizer_id UUID         PRIMARY KEY,
    reason       VARCHAR(500) NOT NULL,
    blocked_by   UUID         NOT NULL,
    blocked_at   TIMESTAMPTZ  NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_payout_blocks_organizer
        FOREIGN KEY (organizer_id) REFERENCES users (id),

    CONSTRAINT fk_payout_blocks_blocked_by
        FOREIGN KEY (blocked_by) REFERENCES users (id)
);

CREATE TABLE audit_log (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_type     VARCHAR(10)  NOT NULL,
    actor_id       UUID,
    action         VARCHAR(50)  NOT NULL,
    target_type    VARCHAR(30)  NOT NULL,
    target_id      UUID         NOT NULL,
    details        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_audit_log_actor
        FOREIGN KEY (actor_id) REFERENCES users (id),

    CONSTRAINT ck_audit_log_actor
        CHECK ((actor_type = 'USER' AND actor_id IS NOT NULL) OR (actor_type = 'SYSTEM' AND actor_id IS NULL))
);

CREATE INDEX idx_audit_log_created ON audit_log (created_at DESC);
CREATE INDEX idx_audit_log_target ON audit_log (target_type, target_id, created_at DESC);
CREATE INDEX idx_audit_log_actor ON audit_log (actor_id, created_at DESC) WHERE actor_id IS NOT NULL;

CREATE FUNCTION reject_audit_log_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION reject_audit_log_change();

CREATE TRIGGER trg_audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION reject_audit_log_change();
