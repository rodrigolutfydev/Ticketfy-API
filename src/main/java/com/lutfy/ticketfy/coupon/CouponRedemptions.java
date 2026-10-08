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
            rs.getBigDecimal("discount_value"));

    private final NamedParameterJdbcTemplate jdbc;

    public CouponRedemptions(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CouponRedemption> findAvailable(UUID eventId, String code, Instant now) {
        var sql = "SELECT id, code, discount_type, discount_value FROM coupons WHERE " + AVAILABLE;
        return jdbc.query(sql, params(eventId, code, now), MAPPER).stream().findFirst();
    }

    public Optional<CouponRedemption> redeem(UUID eventId, String code, Instant now) {
        var sql = """
                UPDATE coupons
                   SET uses_count = uses_count + 1,
                       first_used_at = COALESCE(first_used_at, :now)
                 WHERE """ + AVAILABLE + """
                RETURNING id, code, discount_type, discount_value
                """;
        return jdbc.query(sql, params(eventId, code, now), MAPPER).stream().findFirst();
    }

    private static MapSqlParameterSource params(UUID eventId, String code, Instant now) {
        return new MapSqlParameterSource("eventId", eventId)
                .addValue("code", code)
                .addValue("now", Timestamp.from(now));
    }
}
