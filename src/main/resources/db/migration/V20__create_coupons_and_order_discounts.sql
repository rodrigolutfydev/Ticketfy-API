CREATE TABLE coupons (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID           NOT NULL,
    code            VARCHAR(30)    NOT NULL,
    discount_type   VARCHAR(10)    NOT NULL,
    discount_value  NUMERIC(10, 2) NOT NULL,
    max_uses        INTEGER,
    uses_count      INTEGER        NOT NULL DEFAULT 0,
    starts_at       TIMESTAMPTZ,
    ends_at         TIMESTAMPTZ,
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    first_used_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,

    CONSTRAINT fk_coupons_event
        FOREIGN KEY (event_id) REFERENCES events (id),

    CONSTRAINT uq_coupons_event_code
        UNIQUE (event_id, code),

    CONSTRAINT ck_coupons_code_upper
        CHECK (code = UPPER(code) AND code = BTRIM(code) AND LENGTH(code) >= 3),

    CONSTRAINT ck_coupons_type
        CHECK (discount_type IN ('PERCENT', 'FIXED')),

    CONSTRAINT ck_coupons_value
        CHECK ((discount_type = 'PERCENT' AND discount_value > 0 AND discount_value <= 100)
            OR (discount_type = 'FIXED' AND discount_value > 0)),

    CONSTRAINT ck_coupons_max_uses
        CHECK (max_uses IS NULL OR max_uses > 0),

    CONSTRAINT ck_coupons_uses
        CHECK (uses_count >= 0 AND (max_uses IS NULL OR uses_count <= max_uses)),

    CONSTRAINT ck_coupons_period
        CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at)
);

CREATE INDEX idx_coupons_event ON coupons (event_id, created_at);

CREATE FUNCTION protect_used_coupon() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.first_used_at IS NOT NULL THEN
            RAISE EXCEPTION 'coupon % was already used and cannot be deleted', OLD.id;
        END IF;
        RETURN OLD;
    END IF;
    IF NEW.code <> OLD.code OR NEW.event_id <> OLD.event_id THEN
        RAISE EXCEPTION 'coupon code and event are immutable';
    END IF;
    IF OLD.first_used_at IS NOT NULL
       AND (NEW.discount_type <> OLD.discount_type
            OR NEW.discount_value <> OLD.discount_value
            OR NEW.first_used_at IS DISTINCT FROM OLD.first_used_at) THEN
        RAISE EXCEPTION 'coupon % was already used and its discount cannot change', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_coupons_protect_used
    BEFORE UPDATE OR DELETE ON coupons
    FOR EACH ROW EXECUTE FUNCTION protect_used_coupon();

ALTER TABLE orders
    ADD COLUMN subtotal_amount NUMERIC(10, 2),
    ADD COLUMN discount_amount NUMERIC(10, 2),
    ADD COLUMN coupon_id       UUID,
    ADD COLUMN coupon_code     VARCHAR(30);

UPDATE orders
   SET subtotal_amount = total_amount,
       discount_amount = 0;

ALTER TABLE orders
    ALTER COLUMN subtotal_amount SET NOT NULL,
    ALTER COLUMN discount_amount SET NOT NULL;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_coupon
        FOREIGN KEY (coupon_id) REFERENCES coupons (id);

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_discount
        CHECK (discount_amount >= 0
           AND discount_amount <= subtotal_amount
           AND total_amount = subtotal_amount - discount_amount);

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_coupon_discount
        CHECK ((coupon_id IS NULL AND coupon_code IS NULL AND discount_amount = 0)
            OR (coupon_id IS NOT NULL AND coupon_code IS NOT NULL));

CREATE INDEX idx_orders_coupon ON orders (coupon_id) WHERE coupon_id IS NOT NULL;
