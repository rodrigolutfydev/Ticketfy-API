package com.lutfy.ticketfy.auth.passwordreset;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.infra.mail.EmailMessage;
import com.lutfy.ticketfy.infra.mail.MailConfig;
import com.lutfy.ticketfy.infra.mail.RecordingEmailSender;
import com.lutfy.ticketfy.infra.security.ClientAddressResolver;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "ticketfy.site.base-url=https://ticketfy.example")
class PasswordResetIntegrationTest extends IntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern LINK = Pattern.compile("https://ticketfy\\.example/reset-password\\?token=([A-Za-z0-9_-]{43})");
    private static final String OLD_PASSWORD = "senha-antiga-123";
    private static final String NEW_PASSWORD = "senha-nova-456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingEmailSender mail;

    @Autowired
    @Qualifier(MailConfig.EXECUTOR)
    private ThreadPoolTaskExecutor mailExecutor;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final String ip = "10.%d.%d.%d".formatted(ThreadLocalRandom.current().nextInt(256),
            ThreadLocalRandom.current().nextInt(256), ThreadLocalRandom.current().nextInt(1, 255));

    @Test
    void unknownEmailIsAcceptedWithoutSendingAnything() throws Exception {
        var email = "ninguem-" + UUID.randomUUID() + "@test.com";

        requestReset(email).andExpect(status().isAccepted()).andExpect(content().string(""));

        RecordingEmailSender.awaitIdle(mailExecutor);
        assertThat(mail.sentTo(email)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'PASSWORD_RESET_REQUESTED' "
                + "AND details::text LIKE ?", Long.class, "%" + email + "%")).isZero();
    }

    @Test
    void validTokenChangesThePasswordAndSignsOutEverySession() throws Exception {
        var user = user("pt-BR", OLD_PASSWORD);
        var session = authSessions.start(user);

        requestReset(user.getEmail()).andExpect(status().isAccepted());
        var message = single(user);
        var token = token(message);

        assertThat(message.subject()).isEqualTo("Redefina a senha da sua conta Ticketfy");
        assertThat(message.text())
                .contains("Olá, Test User!", "Redefinir senha: https://ticketfy.example/reset-password?token=" + token,
                        "30 minutos", "Se não foi você que pediu, pode ignorar este e-mail");
        assertThat(message.html())
                .contains("lang=\"pt-BR\"", "href=\"https://ticketfy.example/reset-password?token=" + token + "\"");
        assertThat(message.attachments()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ? AND token_hash = ?",
                Long.class, user.getId(), PasswordResetService.hash(token))).isEqualTo(1);

        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());

        login(user.getEmail(), OLD_PASSWORD).andExpect(status().isUnauthorized());
        login(user.getEmail(), NEW_PASSWORD).andExpect(status().isOk());
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT revoked_reason FROM auth_sessions WHERE user_id = ? ORDER BY created_at LIMIT 1",
                String.class, user.getId())).isEqualTo("PASSWORD_CHANGED");
        assertThat(jdbc.queryForObject("SELECT used_at IS NOT NULL FROM password_reset_tokens WHERE user_id = ?",
                Boolean.class, user.getId())).isTrue();

        var audit = jdbc.queryForList("SELECT action, actor_type, details::text AS details FROM audit_log "
                + "WHERE target_id = ? ORDER BY created_at, action", user.getId());
        assertThat(audit).extracting(row -> row.get("action"))
                .containsExactly("PASSWORD_RESET_REQUESTED", "PASSWORD_RESET");
        assertThat(audit).extracting(row -> row.get("actor_type")).containsOnly("SYSTEM");
        assertThat(audit).extracting(row -> (String) row.get("details"))
                .noneMatch(details -> details.contains(token) || details.contains(NEW_PASSWORD));
        assertThat((String) audit.get(1).get("details")).contains("\"revokedSessions\": 1", "\"firstPassword\": false");
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        var user = user("pt-BR", OLD_PASSWORD);
        requestReset(user.getEmail()).andExpect(status().isAccepted());
        var token = token(single(user));
        jdbc.update("UPDATE password_reset_tokens SET created_at = NOW() - INTERVAL '31 minutes', "
                + "expires_at = NOW() - INTERVAL '1 minute' WHERE user_id = ?", user.getId());

        expectProblem(confirm(token, NEW_PASSWORD), 400, "password-reset-token-expired");
        login(user.getEmail(), OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void tokenCannotBeUsedTwice() throws Exception {
        var user = user("pt-BR", OLD_PASSWORD);
        requestReset(user.getEmail()).andExpect(status().isAccepted());
        var token = token(single(user));

        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
        expectProblem(confirm(token, "outra-senha-789"), 400, "password-reset-token-used");

        login(user.getEmail(), NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void newRequestInvalidatesThePreviousToken() throws Exception {
        var user = user("pt-BR", OLD_PASSWORD);
        requestReset(user.getEmail()).andExpect(status().isAccepted());
        var first = token(single(user));
        requestReset(user.getEmail()).andExpect(status().isAccepted());
        var messages = mail.await(user.getEmail(), 2);
        var second = token(messages.get(1));

        assertThat(second).isNotEqualTo(first);
        expectProblem(confirm(first, NEW_PASSWORD), 400, "password-reset-token-invalid");
        confirm(second, NEW_PASSWORD).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ? "
                + "AND invalidated_at IS NOT NULL", Long.class, user.getId())).isEqualTo(1);
    }

    @Test
    void unknownOrMalformedTokenIsInvalid() throws Exception {
        expectProblem(confirm("x".repeat(43), NEW_PASSWORD), 400, "password-reset-token-invalid");
        expectProblem(confirm("curto", NEW_PASSWORD), 400, "password-reset-token-invalid");
    }

    @Test
    void suspendedAccountGetsNoTokenAndCannotUseAnOldOne() throws Exception {
        var user = user("pt-BR", OLD_PASSWORD);
        requestReset(user.getEmail()).andExpect(status().isAccepted());
        var token = token(single(user));
        suspend(user);

        requestReset(user.getEmail()).andExpect(status().isAccepted());
        RecordingEmailSender.awaitIdle(mailExecutor);
        assertThat(mail.sentTo(user.getEmail())).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?",
                Long.class, user.getId())).isEqualTo(1);

        expectProblem(confirm(token, NEW_PASSWORD), 400, "password-reset-token-invalid");
    }

    @Test
    void googleOnlyAccountSetsItsFirstPassword() throws Exception {
        var id = UUID.randomUUID();
        var email = id + "@test.com";
        jdbc.update("INSERT INTO users (id, name, email, google_subject, role, language) "
                + "VALUES (?, 'Jane Doe', ?, ?, 'USER', 'en')", id, email, "google-" + id);
        var user = userRepository.findById(id).orElseThrow();

        requestReset(email).andExpect(status().isAccepted());
        var message = single(user);
        assertThat(message.subject()).isEqualTo("Set a password for your Ticketfy account");
        assertThat(message.text()).contains("Hi, Jane Doe!", "signs in with Google", "Set password: ",
                "valid for 30 minutes", "you can ignore this email");
        assertThat(message.html()).contains("lang=\"en\"");

        confirm(token(message), NEW_PASSWORD).andExpect(status().isNoContent());

        login(email, NEW_PASSWORD).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT details::text FROM audit_log WHERE target_id = ? AND action = 'PASSWORD_RESET'",
                String.class, id)).contains("\"firstPassword\": true");
    }

    @Test
    void requestsAreLimitedPerEmailAndPerIp() throws Exception {
        var email = "limite-" + UUID.randomUUID() + "@test.com";
        for (int i = 0; i < 3; i++) {
            requestReset(email).andExpect(status().isAccepted());
        }
        requestReset(email)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type", endsWith("/too-many-password-reset-attempts")));
        requestReset(email, "10.255.255.1").andExpect(status().isTooManyRequests());

        // As 4 tentativas acima (inclusive a recusada) já contam para o IP: o limite é 10.
        for (int i = 0; i < 6; i++) {
            requestReset("outro-" + UUID.randomUUID() + "@test.com").andExpect(status().isAccepted());
        }
        expectProblem(requestReset("mais-um-" + UUID.randomUUID() + "@test.com"), 429, "too-many-password-reset-attempts");
    }

    @Test
    void confirmationsAreLimitedPerIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            confirm("x".repeat(43), NEW_PASSWORD).andExpect(status().isBadRequest());
        }
        expectProblem(confirm("x".repeat(43), NEW_PASSWORD), 429, "too-many-password-reset-attempts");
    }

    @Test
    void weakPasswordIsRejectedAndKeepsTheTokenUsable() throws Exception {
        var user = user("pt-BR", OLD_PASSWORD);
        requestReset(user.getEmail()).andExpect(status().isAccepted());
        var token = token(single(user));

        expectProblem(confirm(token, "curta"), 400, "validation-failed")
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));

        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void routesNeedTheFrontendOriginLikeLogin() throws Exception {
        mockMvc.perform(post("/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("email", "a@test.com"))))
                .andExpect(status().isForbidden());
    }

    private ResultActions requestReset(String email) throws Exception {
        return requestReset(email, ip);
    }

    private ResultActions requestReset(String email, String clientIp) throws Exception {
        return mockMvc.perform(post("/auth/password-reset/request")
                .with(fromFrontend())
                .header(ClientAddressResolver.CLIENT_IP_HEADER, clientIp)
                .header(ClientAddressResolver.PROXY_SECRET_HEADER, PROXY_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("email", email))));
    }

    private ResultActions confirm(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/auth/password-reset/confirm")
                .with(fromFrontend())
                .header(ClientAddressResolver.CLIENT_IP_HEADER, ip)
                .header(ClientAddressResolver.PROXY_SECRET_HEADER, PROXY_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("token", token, "newPassword", newPassword))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .with(fromFrontend())
                .header(ClientAddressResolver.CLIENT_IP_HEADER, "10.200." + ThreadLocalRandom.current().nextInt(256)
                        + "." + ThreadLocalRandom.current().nextInt(1, 255))
                .header(ClientAddressResolver.PROXY_SECRET_HEADER, PROXY_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private static ResultActions expectProblem(ResultActions result, int status, String slug) throws Exception {
        return result.andExpect(status().is(status)).andExpect(jsonPath("$.type", endsWith("/" + slug)));
    }

    private EmailMessage single(User user) {
        var messages = mail.await(user.getEmail(), 1);
        RecordingEmailSender.awaitIdle(mailExecutor);
        assertThat(mail.sentTo(user.getEmail())).hasSize(messages.size());
        return messages.get(0);
    }

    private static String token(EmailMessage message) {
        var matcher = LINK.matcher(message.text());
        assertThat(matcher.find()).as("reset link in the email").isTrue();
        return matcher.group(1);
    }

    private User user(String language, String password) {
        var id = insertUser("USER");
        jdbc.update("UPDATE users SET language = ?, password = ? WHERE id = ?",
                language, passwordEncoder.encode(password), id);
        return userRepository.findById(id).orElseThrow();
    }

    private void suspend(User user) {
        var admin = insertUser("ADMIN");
        jdbc.update("UPDATE users SET suspended_at = NOW(), suspension_reason = 'teste', suspended_by = ? WHERE id = ?",
                admin, user.getId());
    }
}
