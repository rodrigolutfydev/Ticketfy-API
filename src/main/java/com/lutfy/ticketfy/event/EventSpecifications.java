package com.lutfy.ticketfy.event;

import com.lutfy.ticketfy.tickettype.TicketType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

final class EventSpecifications {

    private static final char ESCAPE = '\\';

    private EventSpecifications() {
    }

    static Specification<Event> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    static Specification<Event> isNotCancelled() {
        return (root, query, cb) -> cb.isNull(root.get("cancelledAt"));
    }

    static Specification<Event> nameContains(String name) {
        var pattern = "%" + escapeLike(name) + "%";
        return (root, query, cb) -> cb.like(cb.upper(root.get("name")), cb.upper(bound(cb, pattern)), ESCAPE);
    }

    static Specification<Event> cityEquals(String city) {
        return (root, query, cb) -> cb.equal(cb.upper(root.get("city")), cb.upper(bound(cb, city)));
    }

    static Specification<Event> isFeatured(boolean featured) {
        return (root, query, cb) -> cb.equal(root.get("featured"), featured);
    }

    static Specification<Event> isSoldOut(boolean soldOut) {
        Specification<Event> notSoldOut = (root, query, cb) -> cb.or(
                cb.not(hasActiveTicketType(root, query, cb, false)),
                hasActiveTicketType(root, query, cb, true));
        return soldOut ? Specification.not(notSoldOut) : notSoldOut;
    }

    private static Predicate hasActiveTicketType(Root<Event> event, CriteriaQuery<?> query, CriteriaBuilder cb,
                                                 boolean withStock) {
        var subquery = query.subquery(Integer.class);
        var ticketType = subquery.from(TicketType.class);
        var conditions = cb.and(
                cb.equal(ticketType.get("event"), event),
                cb.isTrue(ticketType.get("active")));
        if (withStock) {
            conditions = cb.and(conditions,
                    cb.lessThan(ticketType.<Integer>get("quantitySold"), ticketType.<Integer>get("quantityTotal")));
        }
        subquery.select(cb.literal(1)).where(conditions);
        return cb.exists(subquery);
    }

    private static Expression<String> bound(CriteriaBuilder cb, String value) {
        return ((HibernateCriteriaBuilder) cb).value(value);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
