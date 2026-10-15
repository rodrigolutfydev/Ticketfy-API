package com.lutfy.ticketfy.auth.google;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

// Google simulado para os testes, sem rede: um servidor HTTP local com o
// endpoint de token e as chaves públicas (JWKS). Cada authorize() registra um
// código de uso único com o code_verifier e a redirect URI esperados, como o
// Google faz depois que o usuário aceita o login.
//
// Uma instância só para a JVM inteira (shared()), configurada no
// IntegrationTestBase: assim todas as classes de teste usam os mesmos
// contextos Spring, sem um contexto a mais só para o login com Google.
public final class FakeGoogle {

    public static final String CLIENT_ID = "ticketfy-test.apps.googleusercontent.com";
    public static final String CLIENT_SECRET = "test-google-client-secret";
    public static final String REDIRECT_URI = "http://localhost:5173/auth/google/callback";
    static final String ISSUER = "https://accounts.google.com";

    private static final FakeGoogle SHARED = new FakeGoogle();

    private final HttpServer server;
    private final RSAKey key;
    private final RSAKey otherKey;
    private final Map<String, Grant> grants = new ConcurrentHashMap<>();
    private final AtomicInteger tokenRequests = new AtomicInteger();
    private volatile int forcedTokenStatus;

    public static FakeGoogle shared() {
        return SHARED;
    }

    private FakeGoogle() {
        try {
            key = new RSAKeyGenerator(2048).keyID("google-test-key").generate();
            otherKey = new RSAKeyGenerator(2048).keyID("google-test-key").generate();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (JOSEException | IOException ex) {
            throw new IllegalStateException(ex);
        }
        server.createContext("/token", this::token);
        server.createContext("/certs", this::certs);
        server.start();
    }

    public String tokenUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/token";
    }

    public String jwkSetUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/certs";
    }

    int tokenRequests() {
        return tokenRequests.get();
    }

    /** O endpoint de token passa a responder com este status (0 volta ao normal). */
    void failTokenEndpoint(int status) {
        forcedTokenStatus = status;
    }

    void reset() {
        grants.clear();
        tokenRequests.set(0);
        forcedTokenStatus = 0;
    }

    /** Registra um login aceito e devolve o código; o id_token usa as claims padrão mais os ajustes. */
    String authorize(String codeVerifier, String redirectUri, Consumer<JWTClaimsSet.Builder> claims) {
        return authorize(codeVerifier, redirectUri, claims, false);
    }

    /** Igual, mas assina o id_token com uma chave que o Google não publica. */
    String authorizeSignedWithUnknownKey(String codeVerifier, String redirectUri, Consumer<JWTClaimsSet.Builder> claims) {
        return authorize(codeVerifier, redirectUri, claims, true);
    }

    static Consumer<JWTClaimsSet.Builder> person(String subject, String email, String name) {
        return builder -> builder.subject(subject).claim("email", email).claim("name", name);
    }

    private String authorize(String codeVerifier, String redirectUri, Consumer<JWTClaimsSet.Builder> claims,
                             boolean unknownKey) {
        var now = Instant.now();
        var builder = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(CLIENT_ID)
                .subject(String.valueOf(System.nanoTime()))
                .claim("email", "person-" + UUID.randomUUID() + "@gmail.com")
                .claim("email_verified", true)
                .claim("name", "Pessoa do Google")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)));
        claims.accept(builder);
        var code = "code-" + UUID.randomUUID();
        grants.put(code, new Grant(codeVerifier, redirectUri, sign(builder.build(), unknownKey ? otherKey : key)));
        return code;
    }

    private static String sign(JWTClaimsSet claims, RSAKey signingKey) {
        try {
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void token(HttpExchange exchange) throws IOException {
        tokenRequests.incrementAndGet();
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "{}");
            return;
        }
        if (forcedTokenStatus != 0) {
            respond(exchange, forcedTokenStatus, "{\"error\":\"internal_failure\"}");
            return;
        }
        var form = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (!"authorization_code".equals(form.get("grant_type"))
                || !CLIENT_ID.equals(form.get("client_id"))
                || !CLIENT_SECRET.equals(form.get("client_secret"))) {
            respond(exchange, 401, "{\"error\":\"invalid_client\"}");
            return;
        }
        var grant = grants.remove(String.valueOf(form.get("code")));
        if (grant == null
                || !grant.codeVerifier().equals(form.get("code_verifier"))
                || !grant.redirectUri().equals(form.get("redirect_uri"))) {
            respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        respond(exchange, 200, """
                {"access_token":"ya29.test","expires_in":3599,"token_type":"Bearer",\
                "scope":"openid email profile","id_token":"%s"}""".formatted(grant.idToken()));
    }

    private void certs(HttpExchange exchange) throws IOException {
        respond(exchange, 200, new JWKSet(List.of(key.toPublicJWK())).toString());
    }

    private static Map<String, String> parseForm(String body) {
        var form = new HashMap<String, String>();
        for (var pair : body.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                form.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return form;
    }

    private static void respond(HttpExchange exchange, int status, String body) {
        try (exchange) {
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private record Grant(String codeVerifier, String redirectUri, String idToken) {
    }
}
