package com.lutfy.ticketfy.auth.google;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleSettingsTest {

    private static final String TOKEN = "https://oauth2.googleapis.com/token";
    private static final String CERTS = "https://www.googleapis.com/oauth2/v3/certs";

    @Test
    void enabledOnlyWithClientIdSecretAndRedirectUris() {
        assertThat(settings("id", "secret", "https://ticketfy.example/cb", true).enabled()).isTrue();
        assertThat(settings("", "", "", true).enabled()).isFalse();
        assertThat(settings("id", "", "https://ticketfy.example/cb", true).enabled()).isFalse();
        assertThat(settings("id", "secret", " ", true).enabled()).isFalse();
    }

    @Test
    void redirectUriMustMatchExactly() {
        var settings = settings("id", "secret", " https://ticketfy.example/cb , http://localhost:5173/cb ", false);

        assertThat(settings.isAllowedRedirectUri("https://ticketfy.example/cb")).isTrue();
        assertThat(settings.isAllowedRedirectUri("http://localhost:5173/cb")).isTrue();
        assertThat(settings.isAllowedRedirectUri("https://ticketfy.example/cb/")).isFalse();
        assertThat(settings.isAllowedRedirectUri("https://ticketfy.example/cb?x=1")).isFalse();
        assertThat(settings.isAllowedRedirectUri(null)).isFalse();
    }

    @Test
    void disabledSettingsAllowNoRedirectUri() {
        var settings = settings("id", "", "https://ticketfy.example/cb", true);

        assertThat(settings.isAllowedRedirectUri("https://ticketfy.example/cb")).isFalse();
    }

    @Test
    void redirectUrisMustBeHttpsExceptLocalhostOutsideProd() {
        assertThat(settings("id", "secret", "http://localhost:5173/cb", false).enabled()).isTrue();
        assertThatThrownBy(() -> settings("id", "secret", "http://localhost:5173/cb", true))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> settings("id", "secret", "http://ticketfy.example/cb", false))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> settings("id", "secret", "https://ticketfy.example/cb#frag", false))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> settings("id", "secret", "/auth/google/callback", false))
                .isInstanceOf(IllegalStateException.class);
    }

    private static GoogleSettings settings(String id, String secret, String uris, boolean production) {
        return new GoogleSettings(id, secret, uris, TOKEN, CERTS, Duration.ofSeconds(10), production);
    }
}
