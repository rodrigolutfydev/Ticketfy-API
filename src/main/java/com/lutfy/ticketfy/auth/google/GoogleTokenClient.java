package com.lutfy.ticketfy.auth.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;

// Troca o código de autorização (com o code_verifier do PKCE) pelo id_token
// no endpoint de token do Google.
@Component
public class GoogleTokenClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenClient.class);

    private final GoogleSettings settings;
    private final RestClient restClient;

    public GoogleTokenClient(GoogleSettings settings) {
        this.settings = settings;
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.timeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public String exchange(String code, String codeVerifier, String redirectUri) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("code_verifier", codeVerifier);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", settings.clientId());
        form.add("client_secret", settings.clientSecret());

        TokenResponse response;
        try {
            response = restClient.post()
                    .uri(settings.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                log.info("Google refused the authorization code: status {}", ex.getStatusCode().value());
                throw authorizationFailed();
            }
            log.warn("Google token endpoint failed: status {}", ex.getStatusCode().value());
            throw unavailable();
        } catch (RestClientException ex) {
            log.warn("Google token endpoint unreachable: {}", ex.getClass().getSimpleName());
            throw unavailable();
        }
        if (response == null || response.idToken() == null || response.idToken().isBlank()) {
            log.warn("Google token response without id_token");
            throw authorizationFailed();
        }
        return response.idToken();
    }

    static ProblemException authorizationFailed() {
        return new ProblemException(ProblemType.GOOGLE_AUTHORIZATION_FAILED, "Google sign-in failed. Try again.");
    }

    private static ProblemException unavailable() {
        return new ProblemException(ProblemType.GOOGLE_UNAVAILABLE, "Google did not respond. Try again later.");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("id_token") String idToken) {
    }
}
