package com.lutfy.ticketfy.event;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

final class EventSpecifications {

    private static final char ESCAPE = '\\';

    private EventSpecifications() {
    }

    static Specification<Event> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
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

    private static Expression<String> bound(CriteriaBuilder cb, String value) {
        return ((HibernateCriteriaBuilder) cb).value(value);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
