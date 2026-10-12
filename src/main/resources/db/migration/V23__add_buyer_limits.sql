ALTER TABLE ticket_types
    ADD COLUMN max_per_buyer INTEGER;

ALTER TABLE ticket_types
    ADD CONSTRAINT ck_ticket_types_max_per_buyer
        CHECK (max_per_buyer IS NULL OR max_per_buyer > 0);

ALTER TABLE coupons
    ADD COLUMN max_uses_per_buyer INTEGER;

ALTER TABLE coupons
    ADD CONSTRAINT ck_coupons_max_uses_per_buyer
        CHECK (max_uses_per_buyer IS NULL OR max_uses_per_buyer > 0);

CREATE TABLE ticket_type_buyer_counts (
    ticket_type_id UUID    NOT NULL,
    user_id        UUID    NOT NULL,
    quantity       INTEGER NOT NULL,

    CONSTRAINT pk_ticket_type_buyer_counts
        PRIMARY KEY (ticket_type_id, user_id),

    CONSTRAINT fk_ticket_type_buyer_counts_ticket_type
        FOREIGN KEY (ticket_type_id) REFERENCES ticket_types (id) ON DELETE CASCADE,

    CONSTRAINT fk_ticket_type_buyer_counts_user
        FOREIGN KEY (user_id) REFERENCES users (id),

    CONSTRAINT ck_ticket_type_buyer_counts_quantity
        CHECK (quantity >= 0)
);

CREATE INDEX idx_ticket_type_buyer_counts_user ON ticket_type_buyer_counts (user_id);

CREATE TABLE coupon_buyer_uses (
    coupon_id UUID    NOT NULL,
    user_id   UUID    NOT NULL,
    uses      INTEGER NOT NULL,

    CONSTRAINT pk_coupon_buyer_uses
        PRIMARY KEY (coupon_id, user_id),

    CONSTRAINT fk_coupon_buyer_uses_coupon
        FOREIGN KEY (coupon_id) REFERENCES coupons (id) ON DELETE CASCADE,

    CONSTRAINT fk_coupon_buyer_uses_user
        FOREIGN KEY (user_id) REFERENCES users (id),

    CONSTRAINT ck_coupon_buyer_uses_uses
        CHECK (uses >= 0)
);

CREATE INDEX idx_coupon_buyer_uses_user ON coupon_buyer_uses (user_id);

INSERT INTO ticket_type_buyer_counts (ticket_type_id, user_id, quantity)
SELECT oi.ticket_type_id, o.user_id, SUM(oi.quantity)
  FROM order_items oi
  JOIN orders o ON o.id = oi.order_id
 WHERE o.status IN ('PENDING', 'PAID')
 GROUP BY oi.ticket_type_id, o.user_id;

INSERT INTO coupon_buyer_uses (coupon_id, user_id, uses)
SELECT o.coupon_id, o.user_id, COUNT(*)
  FROM orders o
 WHERE o.coupon_id IS NOT NULL
   AND o.status IN ('PENDING', 'PAID', 'REFUNDED')
 GROUP BY o.coupon_id, o.user_id;
