package com.lutfy.ticketfy.auth.google;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.auth.AuthSessionService;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class GoogleLoginIntegrationTest extends IntegrationTestBase {

    private static final String PROBLEMS = "https://ticketfy-api.onrender.com/problems/";
    private static final String COOKIE = "__Host-ticketfy_rt";
    private static final String REDIRECT_URI = FakeGoogle.REDIRECT_URI;
    private static final String VERIFIER = "pkce-verifier-" + "a".repeat(40);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final FakeGoogle GOOGLE = FakeGoogle.shared();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthSessionService sessions;

    @BeforeEach
    void resetGoogle() {
        GOOGLE.reset();
    }

    @Test
    void firstGoogleLoginCreatesAnAccountWithoutPasswordAndOpensTheSameSession() throws Exception {
        var subject = uniqueSubject();
        var email = "nova." + subject + "@gmail.com";
        var code = GOOGLE.authorize(VERIFIER, REDIRECT_URI, FakeGoogle.person(subject, email, "Nova Pessoa"));

        var result = google(code, VERIFIER, REDIRECT_URI)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();

        var setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith(COOKIE + "=").contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/");

        var user = userRepository.findByGoogleSubject(subject).orElseThrow();
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getName()).isEqualTo("Nova Pessoa");
        assertThat(user.getRole().name()).isEqualTo("USER");
        assertThat(user.getPassword()).isNull();
        assertThat(activeSessions(user.getId())).isEqualTo(1);

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + bodyToken(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.hasPassword").value(false))
                .andExpect(jsonPath("$.googleLinked").value(true));
    }

    @Test
    void sameGoogleAccountSignsInToTheSameUser() throws Exception {
        var subject = uniqueSubject();
        var email = "volta." + subject + "@gmail.com";
        google(GOOGLE.authorize(VERIFIER, REDIRECT_URI, FakeGoogle.person(subject, email, "Volta"))).andExpect(status().isOk());
        var first = userRepository.findByGoogleSubject(subject).orElseThrow();

        // O e-mail no Google pode mudar: a conta é achada pelo sub.
        google(GOOGLE.authorize(VERIFIER, REDIRECT_URI, FakeGoogle.person(subject, "outro." + email, "Volta")))
                .andExpect(status().isOk());

        assertThat(userRepository.findByGoogleSubject(subject).orElseThrow().getId()).isEqualTo(first.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE google_subject = ?", Integer.class, subject))
                .isEqualTo(1);
        assertThat(activeSessions(first.getId())).isEqualTo(2);
    }

    @Test
    void verifiedEmailLinksTheExistingAccountAndRemovesItsPassword() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var registeredEmail = "Ana.Lima." + suffix + "@Mail.com";
        var userId = userWithPassword(registeredEmail, "Senha-forte-123");
        var oldSession = sessions.start(userRepository.findById(userId).orElseThrow());
        var subject = uniqueSubject();

        google(GOOGLE.authorize(VERIFIER, REDIRECT_URI,
                FakeGoogle.person(subject, "ana.lima." + suffix + "@mail.com", "Ana do Google")))
                .andExpect(status().isOk());

        var user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getGoogleSubject()).isEqualTo(subject);
        assertThat(user.hasPassword()).isFalse();
        assertThat(user.getName()).isEqualTo("Test User");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE lower(email) = lower(?)", Integer.class,
                "ana.lima." + suffix + "@mail.com")).isEqualTo(1);
        // A sessão que já estava aberta cai: o dono do e-mail acabou de provar quem é.
        assertThat(jdbc.queryForObject("SELECT revoked_reason FROM auth_sessions WHERE id = ?", String.class,
                sessionId(oldSession.accessToken()))).isEqualTo("GOOGLE_LINKED");
        assertThat(activeSessions(userId)).isEqualTo(1);
        var audit = jdbc.queryForMap("""
                SELECT details->>'revokedSessions' AS revoked, details->>'credentialRemoved' AS removed FROM audit_log
                 WHERE action = 'GOOGLE_ACCOUNT_LINKED' AND target_id = ?
                """, userId);
        assertThat(audit).containsEntry("revoked", "1").containsEntry("removed", "true");

        // A senha antiga (que pode ter sido cadastrada por outra pessoa) não entra mais.
        problem(login(registeredEmail, "Senha-forte-123"), 401, "invalid-credentials");

        // O Google continua entrando na mesma conta.
        var again = google(GOOGLE.authorize(VERIFIER, REDIRECT_URI,
                FakeGoogle.person(subject, "ana.lima." + suffix + "@mail.com", "Ana do Google")))
                .andExpect(status().isOk())
                .andReturn();
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + bodyToken(again)))
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.hasPassword").value(false))
                .andExpect(jsonPath("$.googleLinked").value(true));

        // O dono define uma senha nova sem a antiga e volta a entrar também com ela.
        mockMvc.perform(patch("/users/me/password")
                        .header("Authorization", "Bearer " + bodyToken(again))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("newPassword", "Nova-senha-456"))))
                .andExpect(status().isOk());
        login(registeredEmail, "Nova-senha-456").andExpect(status().isOk());
        problem(login(registeredEmail, "Senha-forte-123"), 401, "invalid-credentials");
    }

    @Test
    void unverifiedEmailIsRefusedWithoutCreatingAnything() throws Exception {
        var subject = uniqueSubject();
        var code = GOOGLE.authorize(VERIFIER, REDIRECT_URI,
                FakeGoogle.person(subject, "sem.verificar." + subject + "@gmail.com", "Sem")
                        .andThen(claims -> claims.claim("email_verified", false)));

        problem(google(code), 403, "google-email-not-verified");

        assertThat(userRepository.findByGoogleSubject(subject)).isEmpty();
    }

    @Test
    void idTokenForAnotherClientIsRefused() throws Exception {
        var subject = uniqueSubject();
        var code = GOOGLE.authorize(VERIFIER, REDIRECT_URI,
                FakeGoogle.person(subject, "aud." + subject + "@gmail.com", "Aud")
                        .andThen(claims -> claims.audience("another-app.apps.googleusercontent.com")));

        problem(google(code), 401, "google-authorization-failed");

        assertThat(userRepository.findByGoogleSubject(subject)).isEmpty();
    }

    @Test
    void expiredIdTokenIsRefused() throws Exception {
        var subject = uniqueSubject();
        var past = Instant.now().minusSeconds(3600);
        var code = GOOGLE.authorize(VERIFIER, REDIRECT_URI,
                FakeGoogle.person(subject, "exp." + subject + "@gmail.com", "Exp")
                        .andThen(claims -> claims.issueTime(Date.from(past.minusSeconds(600)))
                                .expirationTime(Date.from(past))));

        problem(google(code), 401, "google-authorization-failed");

        assertThat(userRepository.findByGoogleSubject(subject)).isEmpty();
    }

    @Test
    void idTokenFromAnotherIssuerIsRefused() throws Exception {
        var code = GOOGLE.authorize(VERIFIER, REDIRECT_URI,
                claims -> claims.issuer("https://accounts.example.com"));

        problem(google(code), 401, "google-authorization-failed");
    }

    @Test
    void idTokenSignedWithAKeyGoogleDoesNotPublishIsRefused() throws Exception {
        var subject = uniqueSubject();
        var code = GOOGLE.authorizeSignedWithUnknownKey(VERIFIER, REDIRECT_URI,
                FakeGoogle.person(subject, "key." + subject + "@gmail.com", "Key"));

        problem(google(code), 401, "google-authorization-failed");

        assertThat(userRepository.findByGoogleSubject(subject)).isEmpty();
    }

    @Test
    void redirectUriOutsideTheListIsRefusedBeforeCallingGoogle() throws Exception {
        var code = GOOGLE.authorize(VERIFIER, "https://evil.example/callback", claims -> {
        });

        problem(google(code, VERIFIER, "https://evil.example/callback"), 400, "google-redirect-uri-not-allowed");

        assertThat(GOOGLE.tokenRequests()).isZero();
    }

    @Test
    void codeRejectedByGoogleFails() throws Exception {
        var code = GOOGLE.authorize(VERIFIER, REDIRECT_URI, claims -> {
        });

        problem(google(code, "another-verifier-" + "b".repeat(40), REDIRECT_URI), 401, "google-authorization-failed");
        // Código de uso único: depois de recusado, nem com o verifier certo.
        problem(google(code), 401, "google-authorization-failed");
    }

    @Test
    void googleOutageIsReportedAsUnavailable() throws Exception {
        GOOGLE.failTokenEndpoint(503);

        problem(google("any-code"), 502, "google-unavailable");
    }

    @Test
    void suspendedAccountGetsTheSameErrorAsThePasswordLogin() throws Exception {
        var subject = uniqueSubject();
        google(GOOGLE.authorize(VERIFIER, REDIRECT_URI, FakeGoogle.person(subject, "susp." + subject + "@gmail.com", "Susp")))
                .andExpect(status().isOk());
        var user = userRepository.findByGoogleSubject(subject).orElseThrow();
        suspend(user.getId());
        int sessionsBefore = allSessions(user.getId());

        problem(google(GOOGLE.authorize(VERIFIER, REDIRECT_URI,
                FakeGoogle.person(subject, "susp." + subject + "@gmail.com", "Susp"))), 403, "account-suspended")
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(allSessions(user.getId())).isEqualTo(sessionsBefore);
    }

    @Test
    void suspendedAccountFoundByEmailIsNotLinked() throws Exception {
        var email = "susp.email." + UUID.randomUUID() + "@mail.com";
        var userId = userWithPassword(email, "Senha-forte-123");
        suspend(userId);

        problem(google(GOOGLE.authorize(VERIFIER, REDIRECT_URI, FakeGoogle.person(uniqueSubject(), email, "Susp"))),
                403, "account-suspended");

        var user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getGoogleSubject()).isNull();
        // Nada foi ligado, então a senha também continua lá.
        assertThat(passwordEncoder.matches("Senha-forte-123", user.getPassword())).isTrue();
    }

    @Test
    void emailAlreadyLinkedToAnotherGoogleAccountIsAConflict() throws Exception {
        var email = "conflito." + UUID.randomUUID() + "@gmail.com";
        google(GOOGLE.authorize(VERIFIER, REDIRECT_URI, FakeGoogle.person(uniqueSubject(), email, "Primeira")))
                .andExpect(status().isOk());

        problem(google(GOOGLE.authorize(VERIFIER, REDIRECT_URI, FakeGoogle.person(uniqueSubject(), email, "Segunda"))),
                409, "google-account-conflict");
    }

    @Test
    void deletedAccountIsNotRevived() throws Exception {
        var subject = uniqueSubject();
        var email = "excluida." + subject + "@gmail.com";
        var first = google(GOOGLE.authorize(VERIFIER, REDIRECT_URI, FakeGoogle.person(subject, email, "Excluída")))
                .andExpect(status().isOk()).andReturn();
        var oldId = userRepository.findByGoogleSubject(subject).orElseThrow().getId();
        mockMvc.perform(post("/users/me/deletion")
                        .header("Authorization", "Bearer " + bodyToken(first))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("email", email, "confirmation", "EXCLUIR"))))
                .andExpect(status().isNoContent());

        google(GOOGLE.authorize(VERIFIER, REDIRECT_URI, FakeGoogle.person(subject, email, "De novo")))
                .andExpect(status().isOk());

        var current = userRepository.findByGoogleSubject(subject).orElseThrow();
        assertThat(current.getId()).isNotEqualTo(oldId);
        assertThat(userRepository.findById(oldId).orElseThrow().getGoogleSubject()).isNull();
    }

    @Test
    void googleAndPasswordLoginShareTheAttemptLimit() throws Exception {
        var address = "google-limit-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/login").with(fromFrontend()).with(from(address))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"nobody@mail.com\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized());
        }
        for (int i = 0; i < 2; i++) {
            googleFrom(address, "unknown-code-" + i).andExpect(status().isUnauthorized());
        }

        googleFrom(address, "unknown-code-last")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "too-many-login-attempts"));
    }

    @Test
    void malformedCodeVerifierIsAValidationError() throws Exception {
        google("code", "short", REDIRECT_URI)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("codeVerifier"));
        assertThat(GOOGLE.tokenRequests()).isZero();
    }

    @Test
    void requestWithoutTheFrontendHeadersIsRejected() throws Exception {
        mockMvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("code", VERIFIER, REDIRECT_URI)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "auth-request-rejected"));
    }

    private ResultActions google(String code) throws Exception {
        return google(code, VERIFIER, REDIRECT_URI);
    }

    private ResultActions google(String code, String verifier, String redirectUri) throws Exception {
        return mockMvc.perform(post("/auth/google").with(fromFrontend()).with(from("google-test-" + UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(code, verifier, redirectUri)));
    }

    private ResultActions googleFrom(String address, String code) throws Exception {
        return mockMvc.perform(post("/auth/google").with(fromFrontend()).with(from(address))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(code, VERIFIER, REDIRECT_URI)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login").with(fromFrontend()).with(from("google-test-" + UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private static String body(String code, String verifier, String redirectUri) {
        return JSON.writeValueAsString(Map.of("code", code, "codeVerifier", verifier, "redirectUri", redirectUri));
    }

    private static ResultActions problem(ResultActions actions, int status, String slug) throws Exception {
        return actions.andExpect(status().is(status))
                .andExpect(jsonPath("$.type").value(PROBLEMS + slug))
                .andExpect(jsonPath("$.status").value(status));
    }

    private UUID userWithPassword(String email, String password) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, name, email, password, role) VALUES (?, ?, ?, ?, 'USER')",
                id, "Test User", email, passwordEncoder.encode(password));
        return id;
    }

    private void suspend(UUID userId) {
        var admin = insertUser("ADMIN");
        jdbc.update("UPDATE users SET suspended_at = NOW(), suspension_reason = 'teste', suspended_by = ? WHERE id = ?",
                admin, userId);
    }

    private int activeSessions(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, userId);
    }

    private int allSessions(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE user_id = ?", Integer.class, userId);
    }

    private static UUID sessionId(String accessToken) {
        return UUID.fromString(com.auth0.jwt.JWT.decode(accessToken).getClaim("sid").asString());
    }

    private static String bodyToken(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString()).get("token").asString();
    }

    private static String uniqueSubject() {
        return String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()));
    }
}
