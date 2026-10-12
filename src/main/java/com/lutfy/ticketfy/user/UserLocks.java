package com.lutfy.ticketfy.user;

import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Component
public class UserLocks {

    public enum AccountState {
        ACTIVE,
        SUSPENDED,
        MISSING
    }

    private final NamedParameterJdbcTemplate jdbc;

    public UserLocks(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AccountState lock(UUID userId) {
        var suspended = jdbc.queryForList("""
                SELECT suspended_at IS NOT NULL FROM users
                 WHERE id = :id AND deleted_at IS NULL
                   FOR KEY SHARE
                """, Map.of("id", userId), Boolean.class);
        if (suspended.isEmpty()) {
            return AccountState.MISSING;
        }
        return suspended.get(0) ? AccountState.SUSPENDED : AccountState.ACTIVE;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockActive(UUID userId) {
        return lock(userId) == AccountState.ACTIVE;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireActive(UUID userId) {
        if (!lockActive(userId)) {
            throw accountDeleted();
        }
    }

    public static ProblemException accountDeleted() {
        return new ProblemException(ProblemType.SESSION_EXPIRED, "Session expired. Sign in again.");
    }

    public static ProblemException accountSuspended() {
        return new ProblemException(ProblemType.ACCOUNT_SUSPENDED, "This account is suspended. Contact support.");
    }
}
