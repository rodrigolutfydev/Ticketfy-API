package com.lutfy.ticketfy.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.lutfy.ticketfy.auth.AuthSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class TokenService {

    private static final String ISSUER = "API Ticketfy";
    private static final String SESSION_CLAIM = "sid";

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final AuthSettings settings;

    public TokenService(@Value("${api.security.token.secret}") String secret, AuthSettings settings, Clock clock) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters long");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = ((JWTVerifier.BaseVerification) JWT.require(algorithm)
                .withIssuer(ISSUER)
                .withClaimPresence(SESSION_CLAIM))
                .build(clock);
        this.settings = settings;
    }

    public String generateToken(UUID userId, UUID sessionId, Instant issuedAt) {
        try {
            return JWT.create()
                    .withIssuer(ISSUER)
                    .withSubject(userId.toString())
                    .withClaim(SESSION_CLAIM, sessionId.toString())
                    .withJWTId(UUID.randomUUID().toString())
                    .withIssuedAt(issuedAt)
                    .withExpiresAt(issuedAt.plus(settings.accessTokenTtl()))
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            throw new IllegalStateException("Error generating JWT token", exception);
        }
    }

    public Optional<AccessTokenClaims> validateToken(String tokenJWT) {
        try {
            var decoded = verifier.verify(tokenJWT);
            var subject = decoded.getSubject();
            var sessionId = decoded.getClaim(SESSION_CLAIM).asString();
            if (subject == null || sessionId == null) {
                return Optional.empty();
            }
            return Optional.of(new AccessTokenClaims(UUID.fromString(subject), UUID.fromString(sessionId)));
        } catch (JWTVerificationException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
