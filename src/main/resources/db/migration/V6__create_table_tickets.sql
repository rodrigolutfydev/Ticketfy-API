CREATE TABLE tickets (
                         id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                         code            VARCHAR(32)  NOT NULL,
                         order_id        UUID         NOT NULL,
                         order_item_id   UUID         NOT NULL,
                         ticket_type_id  UUID         NOT NULL,
                         owner_id        UUID         NOT NULL,
                         status          VARCHAR(20)  NOT NULL DEFAULT 'VALID',
                         used_at         TIMESTAMP,
                         created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
                         updated_at      TIMESTAMP,

                         CONSTRAINT uk_tickets_code
                             UNIQUE (code),

                         CONSTRAINT fk_tickets_order
                             FOREIGN KEY (order_id) REFERENCES orders (id),

                         CONSTRAINT fk_tickets_order_item
                             FOREIGN KEY (order_item_id) REFERENCES order_items (id),

                         CONSTRAINT fk_tickets_ticket_type
                             FOREIGN KEY (ticket_type_id) REFERENCES ticket_types (id),

                         CONSTRAINT fk_tickets_owner
                             FOREIGN KEY (owner_id) REFERENCES users (id),

                         CONSTRAINT ck_tickets_status
                             CHECK (status IN ('VALID', 'USED', 'CANCELLED'))
);

CREATE INDEX idx_tickets_owner_id ON tickets (owner_id);
CREATE INDEX idx_tickets_order_id ON tickets (order_id);