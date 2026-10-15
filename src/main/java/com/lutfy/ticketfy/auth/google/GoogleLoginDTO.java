package com.lutfy.ticketfy.auth.google;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// codeVerifier segue o PKCE (RFC 7636): 43 a 128 caracteres de [A-Za-z0-9-._~].
public record GoogleLoginDTO(
        @NotBlank @Size(max = 2048) String code,
        @NotBlank @Size(min = 43, max = 128) @Pattern(regexp = "[A-Za-z0-9._~-]*") String codeVerifier,
        @NotBlank @Size(max = 2048) String redirectUri
) {
    @Override
    public String toString() {
        return "GoogleLoginDTO[redirectUri=" + redirectUri + "]";
    }
}
