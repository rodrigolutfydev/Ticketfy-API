package com.lutfy.ticketfy.privacy;

import com.lutfy.ticketfy.audit.AuditAction;
import com.lutfy.ticketfy.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountDeletionAtomicityIntegrationTest extends PrivacyTestBase {

    @MockitoSpyBean
    private AuditService auditService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void failureAtTheLastStepLeavesTheAccountUntouched() throws Exception {
        var organizer = person("ORGANIZER", "Lara");
        registerAccount(organizer);
        var emptyEvent = upcomingEvent(organizer);
        var otherOrganizer = person("ORGANIZER", "Mauro");
        var lot = insertTicketType(upcomingEvent(otherOrganizer), "20.00");
        var pendingOrderId = pendingOrder(organizer, lot, 2);
        var token = bearer(organizer);
        var before = jdbc.queryForMap("SELECT name, email, avatar_url, password, deleted_at FROM users WHERE id = ?",
                organizer.getId());
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                doThrow(new IllegalStateException("audit unavailable")).when(auditService)
                        .record(eq(AuditAction.ACCOUNT_DELETED), any(), any(), any()));

        deleteAccount(token, PASSWORD, "EXCLUIR").andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForMap("SELECT name, email, avatar_url, password, deleted_at FROM users WHERE id = ?",
                organizer.getId())).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payout_accounts WHERE organizer_id = ?", Integer.class,
                organizer.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT active FROM events WHERE id = ?", Boolean.class, emptyEvent)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, pendingOrderId))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, lot))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE user_id = ? AND revoked_at IS NOT NULL",
                Integer.class, organizer.getId())).isZero();
        mockMvc.perform(get("/users/me").header("Authorization", token)).andExpect(status().isOk());
    }
}
