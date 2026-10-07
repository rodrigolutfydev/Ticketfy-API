ALTER TABLE orders
    ADD COLUMN platform_fee_percent NUMERIC(5, 2),
    ADD COLUMN platform_fee         NUMERIC(10, 2),
    ADD COLUMN net_amount           NUMERIC(10, 2);

UPDATE orders
   SET platform_fee_percent = 0,
       platform_fee         = 0,
       net_amount           = total_amount;

ALTER TABLE orders
    ALTER COLUMN platform_fee_percent SET NOT NULL,
    ALTER COLUMN platform_fee         SET NOT NULL,
    ALTER COLUMN net_amount           SET NOT NULL;

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_platform_fee_percent
        CHECK (platform_fee_percent BETWEEN 0 AND 100);

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_platform_fee
        CHECK (platform_fee >= 0 AND net_amount = total_amount - platform_fee);

DO $$
BEGIN
    IF EXISTS (SELECT 1
                 FROM order_items oi
                 JOIN ticket_types tt ON tt.id = oi.ticket_type_id
                 JOIN orders o ON o.id = oi.order_id
                WHERE o.status IN ('PAID', 'REFUNDED')
                GROUP BY oi.order_id
               HAVING COUNT(DISTINCT tt.event_id) > 1) THEN
        RAISE EXCEPTION 'Paid orders spanning more than one event must be fixed before V15';
    END IF;
END $$;

CREATE TABLE organizer_ledger_entries (
    id           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id UUID           NOT NULL,
    event_id     UUID           NOT NULL,
    order_id     UUID           NOT NULL,
    type         VARCHAR(20)    NOT NULL,
    amount       NUMERIC(10, 2) NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ledger_entries_organizer
        FOREIGN KEY (organizer_id) REFERENCES users (id),

    CONSTRAINT fk_ledger_entries_event
        FOREIGN KEY (event_id) REFERENCES events (id),

    CONSTRAINT fk_ledger_entries_order
        FOREIGN KEY (order_id) REFERENCES orders (id),

    CONSTRAINT uk_ledger_entries_order_type
        UNIQUE (order_id, type),

    CONSTRAINT ck_ledger_entries_type
        CHECK (type IN ('SALE_CREDIT', 'REFUND_DEBIT')),

    CONSTRAINT ck_ledger_entries_amount
        CHECK ((type = 'SALE_CREDIT' AND amount > 0) OR (type = 'REFUND_DEBIT' AND amount < 0))
);

CREATE INDEX idx_ledger_entries_organizer_created ON organizer_ledger_entries (organizer_id, created_at DESC);
CREATE INDEX idx_ledger_entries_event ON organizer_ledger_entries (event_id);

CREATE FUNCTION reject_ledger_entry_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'organizer_ledger_entries is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON organizer_ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_entry_change();

CREATE TRIGGER trg_ledger_entries_no_truncate
    BEFORE TRUNCATE ON organizer_ledger_entries
    FOR EACH STATEMENT EXECUTE FUNCTION reject_ledger_entry_change();

INSERT INTO organizer_ledger_entries (organizer_id, event_id, order_id, type, amount, created_at)
SELECT e.organizer_id, e.id, o.id, 'SALE_CREDIT', o.net_amount,
       COALESCE((SELECT MIN(p.approved_at) FROM payments p WHERE p.order_id = o.id), o.created_at)
  FROM orders o
  JOIN (SELECT DISTINCT oi.order_id, tt.event_id
          FROM order_items oi
          JOIN ticket_types tt ON tt.id = oi.ticket_type_id) oe ON oe.order_id = o.id
  JOIN events e ON e.id = oe.event_id
 WHERE o.status IN ('PAID', 'REFUNDED')
   AND o.net_amount > 0;

INSERT INTO organizer_ledger_entries (organizer_id, event_id, order_id, type, amount, created_at)
SELECT e.organizer_id, e.id, o.id, 'REFUND_DEBIT', -o.net_amount,
       COALESCE((SELECT MAX(p.refunded_at) FROM payments p WHERE p.order_id = o.id), o.updated_at, o.created_at)
  FROM orders o
  JOIN (SELECT DISTINCT oi.order_id, tt.event_id
          FROM order_items oi
          JOIN ticket_types tt ON tt.id = oi.ticket_type_id) oe ON oe.order_id = o.id
  JOIN events e ON e.id = oe.event_id
 WHERE o.status = 'REFUNDED'
   AND o.net_amount > 0;
