ALTER TABLE tickets
    ADD COLUMN transfer_count INT NOT NULL DEFAULT 0;

ALTER TABLE tickets
    ADD CONSTRAINT ck_tickets_transfer_count CHECK (transfer_count >= 0);

CREATE TABLE ticket_transfers (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id       UUID         NOT NULL,
    from_user_id    UUID         NOT NULL,
    to_user_id      UUID         NOT NULL,
    transferred_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_ticket_transfers_ticket
        FOREIGN KEY (ticket_id) REFERENCES tickets (id),

    CONSTRAINT fk_ticket_transfers_from_user
        FOREIGN KEY (from_user_id) REFERENCES users (id),

    CONSTRAINT fk_ticket_transfers_to_user
        FOREIGN KEY (to_user_id) REFERENCES users (id),

    CONSTRAINT ck_ticket_transfers_distinct_users
        CHECK (from_user_id <> to_user_id)
);

CREATE INDEX idx_ticket_transfers_ticket ON ticket_transfers (ticket_id, transferred_at);

CREATE FUNCTION reject_ticket_transfer_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ticket_transfers is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ticket_transfers_append_only
    BEFORE UPDATE OR DELETE ON ticket_transfers
    FOR EACH ROW EXECUTE FUNCTION reject_ticket_transfer_change();

CREATE TRIGGER trg_ticket_transfers_no_truncate
    BEFORE TRUNCATE ON ticket_transfers
    FOR EACH STATEMENT EXECUTE FUNCTION reject_ticket_transfer_change();
