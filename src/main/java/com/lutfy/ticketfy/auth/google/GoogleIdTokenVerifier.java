package com.lutfy.ticketfy.auth.google;

import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// Valida o id_token devolvido pelo Google: assinatura pelas chaves públicas
// do Google (JWKS, com cache), emissor, audiência igual ao nosso client id e
// validade. email_verified é conferido à parte, com erro próprio.
@Component
public class GoogleIdTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private final JwtDecoder decoder;

    public GoogleIdTokenVerifier(GoogleSettings settings, Clock clock) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.timeout());

        var decoder = NimbusJwtDecoder.withJwkSetUri(settings.jwkSetUri().toString())
                .restOperations(new RestTemplate(requestFactory))
                .build();
        decoder.setJwtValidator(validator(settings.clientId(), clock));
        this.decoder = decoder;
    }

    public GoogleIdentity verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (BadJwtException ex) {
            log.info("Rejected Google id_token: {}", ex.getMessage());
            throw GoogleTokenClient.authorizationFailed();
        } catch (JwtException ex) {
            log.warn("Could not check the Google id_token: {}", ex.getMessage());
            throw new ProblemException(ProblemType.GOOGLE_UNAVAILABLE, "Google did not respond. Try again later.");
        }
        var email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            log.info("Rejected Google id_token without email");
            throw GoogleTokenClient.authorizationFailed();
        }
        if (!isTrue(jwt.getClaims().get("email_verified"))) {
            throw new ProblemException(ProblemType.GOOGLE_EMAIL_NOT_VERIFIED,
                    "This Google account email is not verified");
        }
        return new GoogleIdentity(jwt.getSubject(), email.trim(), jwt.getClaimAsString("name"));
    }

    static OAuth2TokenValidator<Jwt> validator(String clientId, Clock clock) {
        var timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        return new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull),
                timestamps,
                new JwtClaimValidator<Object>(JwtClaimNames.ISS, issuer -> issuer != null && ISSUERS.contains(issuer.toString())),
                new JwtClaimValidator<Object>(JwtClaimNames.AUD, audience ->
                        audience instanceof Collection<?> values && values.contains(clientId)),
                new JwtClaimValidator<Object>(JwtClaimNames.SUB, subject -> subject != null && !subject.toString().isBlank())));
    }

    // O Google manda email_verified como booleano, mas alguns tokens antigos
    // traziam a string "true".
    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equals(value);
    }

    public record GoogleIdentity(String subject, String email, String name) {
    }
}
