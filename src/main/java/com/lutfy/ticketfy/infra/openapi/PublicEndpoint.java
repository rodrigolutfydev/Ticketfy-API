package com.lutfy.ticketfy.infra.openapi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca no contrato OpenAPI uma rota liberada no {@code SecurityConfig}. Sem ela, a rota aparece
 * com o cadeado do bearer JWT. Só documenta: quem libera de verdade é o {@code SecurityConfig}, e o
 * {@code OpenApiContractTest} confere os dois.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PublicEndpoint {
}
