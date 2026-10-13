package com.lutfy.ticketfy.infra.exception;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemReasonTest {

    @Test
    void reasonsAreKebabCaseAndUniqueWithinTheirType() {
        for (var type : ProblemType.values()) {
            var reasons = type.reasons();
            assertThat(reasons).as(type.slug()).doesNotHaveDuplicates();
            assertThat(reasons).as(type.slug()).allMatch(reason -> reason.matches("[a-z]+(-[a-z]+)*"));
        }
        assertThat(Arrays.stream(ProblemReason.values()).map(ProblemReason::type).distinct())
                .contains(ProblemType.INVALID_COUPON_SETTINGS, ProblemType.INVALID_TICKET_TYPE_QUANTITY);
    }

    @Test
    void problemExceptionWithReasonSetsTypeAndReasonProperty() {
        var ex = new ProblemException(ProblemReason.COUPON_MAX_USES_BELOW_USED, "detail",
                Map.of("usesCount", 2));

        assertThat(ex.getType()).isEqualTo(ProblemType.INVALID_COUPON_SETTINGS);
        assertThat(ex.getProperties()).containsEntry("reason", "max-uses-below-used").containsEntry("usesCount", 2);
    }
}
