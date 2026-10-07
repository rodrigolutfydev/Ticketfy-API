package com.lutfy.ticketfy.user;

import com.lutfy.ticketfy.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRoleConstraintIntegrationTest extends IntegrationTestBase {

    @ParameterizedTest
    @EnumSource(Role.class)
    void acceptsEveryRoleOfTheEnum(Role role) {
        var id = insertUser(role.name());

        var stored = jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, id);
        assertThat(stored).isEqualTo(role.name());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPERADMIN", "admin", ""})
    void rejectsRolesOutsideTheEnum(String role) {
        assertThatThrownBy(() -> insertUser(role))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_role");
    }

    @Test
    void rejectsChangingAnExistingUserToAnInvalidRole() {
        var id = insertUser("USER");

        assertThatThrownBy(() -> jdbc.update("UPDATE users SET role = 'ROOT' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
