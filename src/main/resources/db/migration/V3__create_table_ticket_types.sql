CREATE TABLE ticket_types (
                            id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
                            event_id        UUID            NOT NULL,
                            name            VARCHAR(150)    NOT NULL,
                            description     VARCHAR(455),
                            price           NUMERIC(10,2)   NOT NULL,
                            quantity_total  INTEGER         NOT NULL,
                            quantity_sold   INTEGER         NOT NULL DEFAULT 0,
                            max_per_order   INTEGER,
                            active          BOOLEAN         NOT NULL DEFAULT TRUE,
                            created_at      TIMESTAMP       NOT NULL DEFAULT now(),
                            updated_at      TIMESTAMP,

                            CONSTRAINT fk_ticket_types_event FOREIGN KEY (event_id) REFERENCES events(id),
                            CONSTRAINT uq_ticket_types_event_name UNIQUE (event_id, name),
                            CONSTRAINT ck_ticket_types_sold CHECK (quantity_sold >= 0),
                            CONSTRAINT ck_ticket_types_capacity CHECK (quantity_sold <= quantity_total),
                            CONSTRAINT ck_ticket_types_price CHECK (price >= 0)
);
CREATE INDEX idx_ticket_types_event ON ticket_types(event_id);