CREATE TABLE orders (
                        id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
                        user_id          UUID           NOT NULL,
                        status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
                        total_amount     NUMERIC(10, 2) NOT NULL,
                        expires_at       TIMESTAMP      NOT NULL,
                        idempotency_key  VARCHAR(100),
                        created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
                        updated_at       TIMESTAMP,

                        CONSTRAINT fk_orders_user
                            FOREIGN KEY (user_id) REFERENCES users (id),

                        CONSTRAINT uk_orders_idempotency_key
                            UNIQUE (idempotency_key),

                        CONSTRAINT ck_orders_status
                            CHECK (status IN ('PENDING', 'PAID', 'EXPIRED', 'CANCELLED', 'REFUNDED')),

                        CONSTRAINT ck_orders_total_amount
                            CHECK (total_amount >= 0)
);

CREATE INDEX idx_orders_user_id ON orders (user_id);

-- Speeds up the job that expires pending orders
CREATE INDEX idx_orders_status_expires_at ON orders (status, expires_at);

CREATE TABLE order_items (
                             id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
                             order_id        UUID           NOT NULL,
                             ticket_type_id  UUID           NOT NULL,
                             unit_price      NUMERIC(10, 2) NOT NULL,
                             quantity        INTEGER        NOT NULL,
                             created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),

                             CONSTRAINT fk_order_items_order
                                 FOREIGN KEY (order_id) REFERENCES orders (id),

                             CONSTRAINT fk_order_items_ticket_type
                                 FOREIGN KEY (ticket_type_id) REFERENCES ticket_types (id),

                             CONSTRAINT ck_order_items_quantity
                                 CHECK (quantity > 0),

                             CONSTRAINT ck_order_items_unit_price
                                 CHECK (unit_price >= 0)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);