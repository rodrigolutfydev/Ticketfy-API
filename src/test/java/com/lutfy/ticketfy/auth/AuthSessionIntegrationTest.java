package com.lutfy.ticketfy.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserRepository;
import jakarta.servlet.http.Cookie;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthSessionIntegrationTest extends IntegrationTestBase {

    private static final String COOKIE = "__Host-ticketfy_rt";
    private static final String PASSWORD = "Senha-forte-123";
    private static final String PROBLEMS = "https://ticketfy-api.onrender.com/problems/";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void setUp() {
        user = userWithPassword(PASSWORD);
    }

    @Test
    void loginSetsAHardenedCookieAndReturnsOnlyTheAccessToken() throws Exception {
        var result = login(user, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn();

        var setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(COOKIE + "=")
                .contains("Path=/", "Max-Age=1800", "Secure", "HttpOnly", "SameSite=Strict")
                .doesNotContain("Domain=");
        var refreshToken = cookieValue(result);
        assertThat(refreshToken).matches("[A-Za-z0-9_-]{43}");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(refreshToken);

        var stored = jdbc.queryForObject("""
                SELECT t.token_hash FROM refresh_tokens t JOIN auth_sessions s ON s.id = t.session_id
                 WHERE s.user_id = ?
                """, byte[].class, user.getId());
        assertThat(stored).hasSize(32).isEqualTo(AuthSessionService.hash(refreshToken));
    }

    @Test
    void accessTokenIdentifiesTheSessionAndExpiresInTenMinutes() throws Exception {
        var token = JWT.decode(bodyToken(login(user, PASSWORD).andReturn()));

        assertThat(token.getSubject()).isEqualTo(user.getId().toString());
        assertThat(token.getClaim("sid").asString()).isEqualTo(sessionIds(user).get(0).toString());
        assertThat(token.getId()).isNotBlank();
        assertThat(Duration.between(token.getIssuedAtAsInstant(), token.getExpiresAtAsInstant()))
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void wrongPasswordCreatesNoSession() throws Exception {
        login(user, "senha-errada-123").andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(sessionIds(user)).isEmpty();
    }

    @Test
    void refreshRotatesTheTokenAndKeepsTheSession() throws Exception {
        var first = login(user, PASSWORD).andReturn();
        var firstCookie = cookieValue(first);

        var second = refresh(firstCookie)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andReturn();
        var secondCookie = cookieValue(second);

        assertThat(secondCookie).isNotEqualTo(firstCookie);
        assertThat(sessionIds(user)).hasSize(1);
        me(bodyToken(second)).andExpect(status().isOk());
        me(bodyToken(first)).andExpect(status().isOk());

        var oldRow = jdbc.queryForMap("SELECT used_at, replaced_by FROM refresh_tokens WHERE token_hash = ?",
                (Object) AuthSessionService.hash(firstCookie));
        var newId = jdbc.queryForObject("SELECT id FROM refresh_tokens WHERE token_hash = ?", UUID.class,
                (Object) AuthSessionService.hash(secondCookie));
        assertThat(oldRow.get("used_at")).isNotNull();
        assertThat(oldRow.get("replaced_by")).isEqualTo(newId);
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() throws Exception {
        var first = cookieValue(login(user, PASSWORD).andReturn());
        var rotated = refresh(first).andExpect(status().isOk()).andReturn();
        var second = cookieValue(rotated);
        var accessToken = bodyToken(rotated);

        var reuse = refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "session-expired"))
                .andReturn();
        assertThat(reuse.getResponse().getHeader(HttpHeaders.SET_COOKIE)).startsWith(COOKIE + "=;").contains("Max-Age=0");

        refresh(second).andExpect(status().isUnauthorized());
        me(accessToken).andExpect(status().isUnauthorized());
        assertThat(revokedReasons(user)).containsExactly("REUSE_DETECTED");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_log
                 WHERE action = 'SESSION_REUSE_DETECTED' AND target_type = 'USER' AND target_id = ?
                   AND actor_type = 'SYSTEM'
                """, Integer.class, user.getId())).isEqualTo(1);
    }

    @Test
    void reuseDoesNotTouchOtherSessionsOfTheSameUser() throws Exception {
        var stolen = cookieValue(login(user, PASSWORD).andReturn());
        var otherDevice = login(user, PASSWORD).andReturn();
        refresh(stolen).andExpect(status().isOk());

        refresh(stolen).andExpect(status().isUnauthorized());

        me(bodyToken(otherDevice)).andExpect(status().isOk());
        refresh(cookieValue(otherDevice)).andExpect(status().isOk());
    }

    @Test
    void concurrentRefreshWithTheSameTokenHasOneWinnerAndRevokesTheFamily() throws Exception {
        var raw = authSessions.start(user).refreshToken();

        var outcomes = race(() -> authSessions.refresh(raw), () -> authSessions.refresh(raw));

        assertThat(outcomes.stream().filter(Optional::isPresent).count()).isLessThanOrEqualTo(1);
        assertThat(outcomes.stream().filter(Optional::isEmpty).count()).isGreaterThanOrEqualTo(1);
        assertThat(revokedReasons(user)).containsExactly("REUSE_DETECTED");
        for (var outcome : outcomes) {
            outcome.ifPresent(session -> assertThat(authSessions.refresh(session.refreshToken())).isEmpty());
        }
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        var cookie = cookieValue(login(user, PASSWORD).andReturn());
        jdbc.update("""
                UPDATE refresh_tokens SET created_at = NOW() - INTERVAL '31 minutes', expires_at = NOW() - INTERVAL '1 minute'
                 WHERE token_hash = ?
                """, (Object) AuthSessionService.hash(cookie));

        refresh(cookie).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "session-expired"));
    }

    @Test
    void sessionPastItsAbsoluteLimitRejectsRefreshAndAccessTokens() throws Exception {
        var result = login(user, PASSWORD).andReturn();
        jdbc.update("""
                UPDATE auth_sessions SET created_at = NOW() - INTERVAL '13 hours', expires_at = NOW() - INTERVAL '1 hour'
                 WHERE user_id = ?
                """, user.getId());

        me(bodyToken(result)).andExpect(status().isUnauthorized());
        refresh(cookieValue(result)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenNeverOutlivesTheSession() throws Exception {
        var raw = authSessions.start(user).refreshToken();
        jdbc.update("UPDATE auth_sessions SET expires_at = created_at + INTERVAL '5 minutes' WHERE user_id = ?",
                user.getId());

        var rotated = authSessions.refresh(raw).orElseThrow();

        assertThat(rotated.refreshTokenTtl()).isLessThanOrEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void refreshWithoutOrWithAnUnknownCookieIsRejected() throws Exception {
        mockMvc.perform(post("/auth/refresh").with(fromFrontend()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "session-expired"));
        refresh("A".repeat(43)).andExpect(status().isUnauthorized());
        refresh("not a token").andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesOnlyThatSessionImmediately() throws Exception {
        var phone = login(user, PASSWORD).andReturn();
        var laptop = login(user, PASSWORD).andReturn();

        var result = mockMvc.perform(post("/auth/logout").with(fromFrontend())
                        .cookie(new Cookie(COOKIE, cookieValue(phone))))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).startsWith(COOKIE + "=;").contains("Max-Age=0");
        me(bodyToken(phone)).andExpect(status().isUnauthorized());
        refresh(cookieValue(phone)).andExpect(status().isUnauthorized());
        me(bodyToken(laptop)).andExpect(status().isOk());
        assertThat(revokedReasons(user)).containsExactly("LOGOUT");
    }

    @Test
    void logoutIsIdempotent() throws Exception {
        var cookie = cookieValue(login(user, PASSWORD).andReturn());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/auth/logout").with(fromFrontend()).cookie(new Cookie(COOKIE, cookie)))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(post("/auth/logout").with(fromFrontend())).andExpect(status().isNoContent());
        assertThat(revokedReasons(user)).containsExactly("LOGOUT");
    }

    @Test
    void logoutAllRevokesEverySessionOfTheUserOnly() throws Exception {
        var phone = login(user, PASSWORD).andReturn();
        var laptop = login(user, PASSWORD).andReturn();
        var someoneElse = userWithPassword(PASSWORD);
        var otherSession = login(someoneElse, PASSWORD).andReturn();

        mockMvc.perform(post("/auth/logout-all").with(fromFrontend())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bodyToken(phone)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        me(bodyToken(phone)).andExpect(status().isUnauthorized());
        me(bodyToken(laptop)).andExpect(status().isUnauthorized());
        refresh(cookieValue(laptop)).andExpect(status().isUnauthorized());
        assertThat(revokedReasons(user)).containsExactly("LOGOUT_ALL", "LOGOUT_ALL");
        me(bodyToken(otherSession)).andExpect(status().isOk());
    }

    @Test
    void logoutAllRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/auth/logout-all").with(fromFrontend()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeRevokesEverySessionAndStartsANewOne() throws Exception {
        var phone = login(user, PASSWORD).andReturn();
        var laptop = login(user, PASSWORD).andReturn();

        var result = changePassword(bodyToken(phone), PASSWORD, "Nova-senha-456")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("HttpOnly", "Max-Age=1800");
        me(bodyToken(phone)).andExpect(status().isUnauthorized());
        me(bodyToken(laptop)).andExpect(status().isUnauthorized());
        refresh(cookieValue(phone)).andExpect(status().isUnauthorized());
        refresh(cookieValue(laptop)).andExpect(status().isUnauthorized());
        me(bodyToken(result)).andExpect(status().isOk());
        refresh(cookieValue(result)).andExpect(status().isOk());

        assertThat(revokedReasons(user)).containsExactly("PASSWORD_CHANGED", "PASSWORD_CHANGED");
        login(user, PASSWORD).andExpect(status().isUnauthorized());
        login(user, "Nova-senha-456").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_log
                 WHERE action = 'PASSWORD_CHANGED' AND target_id = ? AND actor_id = ?
                """, Integer.class, user.getId(), user.getId())).isEqualTo(1);
    }

    @Test
    void passwordChangeWithTheWrongCurrentPasswordKeepsEverything() throws Exception {
        var phone = login(user, PASSWORD).andReturn();

        changePassword(bodyToken(phone), "senha-errada-123", "Nova-senha-456")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-password"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        me(bodyToken(phone)).andExpect(status().isOk());
        assertThat(revokedReasons(user)).isEmpty();
        login(user, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void passwordChangeValidatesTheNewPassword() throws Exception {
        var token = bodyToken(login(user, PASSWORD).andReturn());

        changePassword(token, PASSWORD, "curta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
        changePassword(null, PASSWORD, "Nova-senha-456").andExpect(status().isUnauthorized());
    }

    @Test
    void legacyTokenWithoutSessionIsRejected() throws Exception {
        var legacy = JWT.create()
                .withIssuer("API Ticketfy")
                .withSubject(user.getEmail())
                .withExpiresAt(Instant.now().plus(Duration.ofHours(2)))
                .sign(Algorithm.HMAC256("test-secret-with-at-least-32-characters"));

        me(legacy).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenForAnotherUsersSessionIsRejected() throws Exception {
        authSessions.start(user);
        var sessionId = sessionIds(user).get(0);
        var forged = JWT.create()
                .withIssuer("API Ticketfy")
                .withSubject(UUID.randomUUID().toString())
                .withClaim("sid", sessionId.toString())
                .withIssuedAt(Instant.now())
                .withExpiresAt(Instant.now().plus(Duration.ofMinutes(5)))
                .sign(Algorithm.HMAC256("test-secret-with-at-least-32-characters"));

        me(forged).andExpect(status().isUnauthorized());
    }

    @Test
    void legacyLoginEndpointIsGone() throws Exception {
        mockMvc.perform(post("/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(user, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void becomingOrganizerKeepsTheCurrentToken() throws Exception {
        var token = accessToken(user);

        mockMvc.perform(post("/users/me/organizer").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        me(token).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ORGANIZER"));
    }

    private User userWithPassword(String password) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, name, email, password, role) VALUES (?, ?, ?, ?, 'USER')",
                id, "Auth User", "auth." + id + "@test.com", passwordEncoder.encode(password));
        return userRepository.findById(id).orElseThrow();
    }

    private ResultActions login(User target, String password) throws Exception {
        return mockMvc.perform(post("/auth/login").with(fromFrontend())
                .with(request -> {
                    request.setRemoteAddr("auth-test-" + UUID.randomUUID());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(target, password)));
    }

    private ResultActions refresh(String cookie) throws Exception {
        return mockMvc.perform(post("/auth/refresh").with(fromFrontend()).cookie(new Cookie(COOKIE, cookie)));
    }

    private ResultActions me(String token) throws Exception {
        return mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions changePassword(String token, String current, String next) throws Exception {
        var request = patch("/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("currentPassword", current, "newPassword", next)));
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private static String credentials(User target, String password) {
        return JSON.writeValueAsString(Map.of("email", target.getEmail(), "password", password));
    }

    private static String bodyToken(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString()).get("token").asString();
    }

    private static String cookieValue(MvcResult result) {
        var header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).startsWith(COOKIE + "=");
        return header.substring(COOKIE.length() + 1, header.indexOf(';'));
    }

    private List<UUID> sessionIds(User target) {
        return jdbc.queryForList("SELECT id FROM auth_sessions WHERE user_id = ? ORDER BY created_at", UUID.class,
                target.getId());
    }

    private List<String> revokedReasons(User target) {
        return jdbc.queryForList("""
                SELECT revoked_reason FROM auth_sessions
                 WHERE user_id = ? AND revoked_reason IS NOT NULL ORDER BY revoked_reason
                """, String.class, target.getId());
    }

    @SafeVarargs
    private static List<Optional<IssuedSession>> race(
            Callable<Optional<IssuedSession>>... actions) throws Exception {
        var ready = new CountDownLatch(actions.length);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(actions.length);
        try {
            var futures = new ArrayList<Future<Optional<IssuedSession>>>();
            for (var action : actions) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return action.call();
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            var outcomes = new ArrayList<Optional<IssuedSession>>();
            for (var future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }
}
