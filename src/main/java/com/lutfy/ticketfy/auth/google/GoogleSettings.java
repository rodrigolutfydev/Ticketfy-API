package com.lutfy.ticketfy.auth.google;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Component
public class GoogleSettings {

    private static final Logger log = LoggerFactory.getLogger(GoogleSettings.class);
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1");

    private final String clientId;
    private final String clientSecret;
    private final List<String> redirectUris;
    private final URI tokenUri;
    private final URI jwkSetUri;
    private final Duration timeout;

    @Autowired
    public GoogleSettings(@Value("${ticketfy.google.client-id:}") String clientId,
                          @Value("${ticketfy.google.client-secret:}") String clientSecret,
                          @Value("${ticketfy.google.redirect-uris:}") String redirectUris,
                          @Value("${ticketfy.google.token-uri}") String tokenUri,
                          @Value("${ticketfy.google.jwk-set-uri}") String jwkSetUri,
                          @Value("${ticketfy.google.timeout-seconds}") long timeoutSeconds,
                          Environment environment) {
        this(clientId, clientSecret, redirectUris, tokenUri, jwkSetUri, Duration.ofSeconds(timeoutSeconds),
                environment.acceptsProfiles(Profiles.of("prod")));
    }

    public GoogleSettings(String clientId, String clientSecret, String redirectUris, String tokenUri,
                          String jwkSetUri, Duration timeout, boolean production) {
        var id = trim(clientId);
        var secret = trim(clientSecret);
        var uris = Arrays.stream(trim(redirectUris).split(","))
                .map(String::trim)
                .filter(uri -> !uri.isEmpty())
                .distinct()
                .toList();
        uris.forEach(uri -> checkRedirectUri(uri, production));
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalStateException("ticketfy.google.timeout-seconds must be positive");
        }

        boolean complete = !id.isEmpty() && !secret.isEmpty() && !uris.isEmpty();
        boolean partial = !complete && (!id.isEmpty() || !secret.isEmpty() || !uris.isEmpty());
        if (partial) {
            log.warn("Google login is disabled: GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET and GOOGLE_REDIRECT_URIS must all be set");
        }
        this.clientId = complete ? id : "";
        this.clientSecret = complete ? secret : "";
        this.redirectUris = complete ? uris : List.of();
        this.tokenUri = URI.create(tokenUri.trim());
        this.jwkSetUri = URI.create(jwkSetUri.trim());
        this.timeout = timeout;
    }

    public boolean enabled() {
        return !clientId.isEmpty();
    }

    public boolean isAllowedRedirectUri(String redirectUri) {
        return redirectUri != null && redirectUris.contains(redirectUri);
    }

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    public URI tokenUri() {
        return tokenUri;
    }

    public URI jwkSetUri() {
        return jwkSetUri;
    }

    public Duration timeout() {
        return timeout;
    }

    // A lista é comparada de forma exata com o redirectUri recebido, então
    // cada entrada precisa ser uma URL absoluta, sem fragmento, e https em
    // produção (http só para localhost, no desenvolvimento).
    private static void checkRedirectUri(String value, boolean production) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("GOOGLE_REDIRECT_URIS has an invalid URL: " + value, ex);
        }
        var scheme = uri.getScheme();
        if (uri.getHost() == null || scheme == null || uri.getRawFragment() != null) {
            throw new IllegalStateException("GOOGLE_REDIRECT_URIS must have absolute URLs without fragment: " + value);
        }
        boolean local = LOCAL_HOSTS.contains(uri.getHost());
        if (!scheme.equals("https") && !(scheme.equals("http") && local && !production)) {
            throw new IllegalStateException("GOOGLE_REDIRECT_URIS must use https (http only for localhost outside prod): " + value);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
