CREATE TABLE payments (
                          id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
                          order_id            UUID           NOT NULL,
                          status              VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
                          method              VARCHAR(20)    NOT NULL,
                          amount              NUMERIC(10, 2) NOT NULL,
                          provider_reference  VARCHAR(100),
                          approved_at         TIMESTAMPTZ,
                          created_at          TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
                          updated_at          TIMESTAMPTZ,

                          CONSTRAINT fk_payments_order
                              FOREIGN KEY (order_id) REFERENCES orders (id),

                          CONSTRAINT ck_payments_status
                              CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),

                          CONSTRAINT ck_payments_method
                              CHECK (method IN ('SIMULATED', 'PIX', 'CREDIT_CARD')),

                          CONSTRAINT ck_payments_amount
                              CHECK (amount >= 0)
);

CREATE INDEX idx_payments_order_id ON payments (order_id);

-- An order can have many payment attempts, but only one approved
CREATE UNIQUE INDEX uk_payments_order_approved
    ON payments (order_id)
    WHERE status = 'APPROVED';