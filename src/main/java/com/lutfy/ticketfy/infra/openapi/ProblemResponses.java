package com.lutfy.ticketfy.infra.openapi;

import com.lutfy.ticketfy.infra.exception.ProblemType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Erros de regra de negócio que a rota pode devolver. Os erros comuns (autenticação, papel, corpo,
 * validação, parâmetros, origem das rotas /auth e erro interno) são deduzidos da assinatura do
 * método pelo {@link ProblemResponsesCustomizer} e não precisam ser repetidos aqui.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ProblemResponses {

    ProblemType[] value();
}
