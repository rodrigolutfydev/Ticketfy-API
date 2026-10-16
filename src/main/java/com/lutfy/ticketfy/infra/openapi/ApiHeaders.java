package com.lutfy.ticketfy.infra.openapi;

/** Descrições dos cabeçalhos de resposta repetidos no contrato OpenAPI. */
public final class ApiHeaders {

    public static final String REFRESH_COOKIE = "__Host-ticketfy_rt";

    public static final String SET_REFRESH_COOKIE = "Grava o refresh token novo no cookie `" + REFRESH_COOKIE
            + "` (`HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/`). O JavaScript da página não consegue lê-lo.";

    public static final String CLEAR_REFRESH_COOKIE = "Apaga o cookie `" + REFRESH_COOKIE + "` (`Max-Age=0`).";

    public static final String ATTACHMENT = "`attachment`, com o nome sugerido para o arquivo.";

    private ApiHeaders() {
    }
}
