package com.lutfy.ticketfy.coupon;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CouponRedemptions {

    private static final String AVAILABLE = """
               event_id = :eventId
           AND code = :code
           AND active
           AND (max_uses IS NULL OR uses_count < max_uses)
           AND (starts_at IS NULL OR starts_at <= :now)
           AND (ends_at IS NULL OR ends_at > :now)
        """;

    private static final RowMapper<CouponRedemption> MAPPER = (rs, i) -> new CouponRedemption(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            DiscountType.valueOf(rs.getString("discount_type")),
            rs.getBigDecimal("discount_value"),
            rs.getObject("max_uses_per_buyer", Integer.class));

    private final NamedParameterJdbcTemplate jdbc;

    public CouponRedemptions(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CouponRedemption> findAvailable(UUID eventId, String code, Instant now) {
        var sql = "SELECT id, code, discount_type, discount_value, max_uses_per_buyer FROM coupons WHERE " + AVAILABLE;
        return jdbc.query(sql, params(eventId, code, now), MAPPER).stream().findFirst();
    }

    public Optional<CouponRedemption> redeem(UUID eventId, String code, Instant now) {
        var sql = """
                UPDATE coupons
                   SET uses_count = uses_count + 1,
                       first_used_at = COALESCE(first_used_at, :now)
                 WHERE """ + AVAILABLE + """
                RETURNING id, code, discount_type, discount_value, max_uses_per_buyer
                """;
        return jdbc.query(sql, params(eventId, code, now), MAPPER).stream().findFirst();
    }

    public boolean consumeBuyerUse(UUID couponId, UUID userId, Integer limit) {
        var params = buyerParams(couponId, userId);
        var upsert = """
                INSERT INTO coupon_buyer_uses (coupon_id, user_id, uses)
                VALUES (:couponId, :userId, 1)
                ON CONFLICT (coupon_id, user_id) DO UPDATE
                   SET uses = coupon_buyer_uses.uses + 1
                """;
        if (limit == null) {
            jdbc.update(upsert, params);
            return true;
        }
        var sql = upsert + """
                 WHERE coupon_buyer_uses.uses + 1 <= :limit
                RETURNING uses
                """;
        return !jdbc.queryForList(sql, params.addValue("limit", limit), Integer.class).isEmpty();
    }

    public void releaseBuyerUse(UUID couponId, UUID userId) {
        jdbc.update("""
                UPDATE coupon_buyer_uses SET uses = uses - 1
                 WHERE coupon_id = :couponId AND user_id = :userId AND uses > 0
                """, buyerParams(couponId, userId));
    }

    public int buyerUses(UUID couponId, UUID userId) {
        return jdbc.queryForList("""
                SELECT uses FROM coupon_buyer_uses WHERE coupon_id = :couponId AND user_id = :userId
                """, buyerParams(couponId, userId), Integer.class).stream().findFirst().orElse(0);
    }

    private static MapSqlParameterSource buyerParams(UUID couponId, UUID userId) {
        return new MapSqlParameterSource("couponId", couponId).addValue("userId", userId);
    }

    private static MapSqlParameterSource params(UUID eventId, String code, Instant now) {
        return new MapSqlParameterSource("eventId", eventId)
                .addValue("code", code)
                .addValue("now", Timestamp.from(now));
    }
}
