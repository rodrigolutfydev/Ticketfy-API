package com.lutfy.ticketfy.auth.google;

import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Sem GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET e GOOGLE_REDIRECT_URIS os beans do
// login com Google sobem sem erro (nada é buscado no Google ao iniciar) e a rota
// responde google-login-unavailable antes de qualquer outra coisa.
class GoogleLoginServiceTest {

    @Test
    void withoutConfigurationTheBeansStartAndTheLoginIsUnavailable() {
        var settings = new GoogleSettings("", "", "", "https://oauth2.googleapis.com/token",
                "https://www.googleapis.com/oauth2/v3/certs", Duration.ofSeconds(10), true);
        var service = new GoogleLoginService(settings, new GoogleTokenClient(settings),
                new GoogleIdTokenVerifier(settings, Clock.systemUTC()), null, null, null, null);

        assertThatThrownBy(() -> service.login(new GoogleLoginDTO("code", "v".repeat(43),
                "https://ticketfy.example/auth/google/callback")))
                .isInstanceOfSatisfying(ProblemException.class,
                        ex -> assertThat(ex.getType()).isEqualTo(ProblemType.GOOGLE_LOGIN_UNAVAILABLE))
                .hasMessage("Google login is not available");
    }
}
