package com.lutfy.ticketfy.infra.openapi;

import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.security.AuthOriginFilter;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Completa cada operação do contrato com o cadeado (quando a rota exige login) e com as respostas
 * de erro em ProblemDetail, agrupadas por status, com um exemplo por tipo.
 */
@Component
public class ProblemResponsesCustomizer implements OperationCustomizer {

    public static final String BEARER = "bearerAuth";
    public static final String PROBLEM_SCHEMA = "#/components/schemas/ProblemDetail";

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final Pattern ROLE = Pattern.compile("'([A-Z_]+)'");
    private static final Map<Integer, String> STATUS_TEXT = Map.of(
            400, "Requisição inválida",
            401, "Não autenticado",
            403, "Acesso negado",
            404, "Não encontrado",
            409, "Conflito com o estado atual",
            422, "Não processável",
            429, "Muitas tentativas",
            500, "Erro interno",
            502, "Falha no Google",
            503, "Indisponível");

    private final String problemsBaseUri;
    private final Validator validator;

    public ProblemResponsesCustomizer(@Value("${ticketfy.problems.base-uri}") String problemsBaseUri,
                                      Validator validator) {
        this.problemsBaseUri = problemsBaseUri.endsWith("/") ? problemsBaseUri : problemsBaseUri + "/";
        this.validator = validator;
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handler) {
        if (!handler.getBeanType().getPackageName().startsWith("com.lutfy.ticketfy")) {
            return operation;
        }
        var types = new LinkedHashSet<ProblemType>();
        if (isAuthRoute(handler)) {
            types.add(ProblemType.AUTH_REQUEST_REJECTED);
            addAuthHeaders(operation);
        }
        if (!handler.hasMethodAnnotation(PublicEndpoint.class)) {
            operation.addSecurityItem(new SecurityRequirement().addList(BEARER));
            types.add(ProblemType.AUTHENTICATION_REQUIRED);
        }
        var roles = requiredRoles(handler);
        if (!roles.isEmpty()) {
            types.add(ProblemType.ACCESS_DENIED);
            describeRoles(operation, roles);
        }
        for (var parameter : handler.getMethodParameters()) {
            types.addAll(parameterProblems(parameter));
        }
        var declared = handler.getMethodAnnotation(ProblemResponses.class);
        if (declared != null) {
            types.addAll(Arrays.asList(declared.value()));
        }
        types.add(ProblemType.INTERNAL_ERROR);
        addResponses(operation, types);
        return operation;
    }

    private Set<ProblemType> parameterProblems(MethodParameter parameter) {
        var problems = new LinkedHashSet<ProblemType>();
        if (parameter.hasParameterAnnotation(RequestBody.class)) {
            problems.add(ProblemType.MALFORMED_REQUEST_BODY);
            if (parameter.hasParameterAnnotation(Valid.class)
                    && validator.getConstraintsForClass(parameter.getParameterType()).isBeanConstrained()) {
                problems.add(ProblemType.VALIDATION_FAILED);
            }
        } else if (Pageable.class.isAssignableFrom(parameter.getParameterType())) {
            problems.add(ProblemType.INVALID_PARAMETER);
        } else if (parameter.hasParameterAnnotation(PathVariable.class)
                || parameter.hasParameterAnnotation(RequestParam.class)) {
            if (parameter.getParameterType() != String.class) {
                problems.add(ProblemType.INVALID_PARAMETER);
            }
            var param = parameter.getParameterAnnotation(RequestParam.class);
            if (param != null && param.required() && ValueConstants.DEFAULT_NONE.equals(param.defaultValue())) {
                problems.add(ProblemType.MISSING_PARAMETER);
            }
        }
        return problems;
    }

    // Mesmo critério do AuthOriginFilter: todo POST em /auth/**.
    private static boolean isAuthRoute(HandlerMethod handler) {
        var type = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), RequestMapping.class);
        var method = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), RequestMapping.class);
        return type != null && method != null
                && Arrays.stream(type.path()).anyMatch(path -> path.equals("/auth") || path.startsWith("/auth/"))
                && Arrays.asList(method.method()).contains(RequestMethod.POST);
    }

    private static void addAuthHeaders(Operation operation) {
        operation.addParametersItem(new HeaderParameter()
                .name("Origin")
                .required(true)
                .description("Origem do site, igual a uma das origens liberadas no CORS. O navegador envia "
                        + "sozinho; por isso estas rotas não funcionam pelo \"Try it out\" do Swagger UI.")
                .schema(new StringSchema().example("https://ticketfy-6fd.pages.dev")));
        operation.addParametersItem(new HeaderParameter()
                .name(AuthOriginFilter.HEADER)
                .required(true)
                .description("Sempre `1`. Um formulário de outro site não consegue enviar este cabeçalho.")
                .schema(new StringSchema()._enum(List.of("1"))));
    }

    private static List<String> requiredRoles(HandlerMethod handler) {
        var preAuthorize = handler.getMethodAnnotation(PreAuthorize.class);
        if (preAuthorize == null) {
            preAuthorize = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), PreAuthorize.class);
        }
        if (preAuthorize == null) {
            return List.of();
        }
        var roles = new ArrayList<String>();
        var matcher = ROLE.matcher(preAuthorize.value());
        while (matcher.find()) {
            roles.add(matcher.group(1));
        }
        return roles;
    }

    private static void describeRoles(Operation operation, List<String> roles) {
        var names = roles.stream().map(role -> "`" + role + "`").collect(Collectors.joining(" ou "));
        var note = "**Papel exigido:** " + names + ".";
        operation.setDescription(operation.getDescription() == null || operation.getDescription().isBlank()
                ? note
                : note + "\n\n" + operation.getDescription());
    }

    private void addResponses(Operation operation, Set<ProblemType> types) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        var byStatus = types.stream().collect(Collectors.groupingBy(type -> type.status().value(), TreeMap::new,
                Collectors.toCollection(ArrayList::new)));
        byStatus.forEach((status, statusTypes) -> {
            var media = new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA));
            statusTypes.forEach(type -> media.addExamples(type.slug(), new Example()
                    .summary(type.slug())
                    .value(example(type))));
            var response = new ApiResponse()
                    .description(description(status, statusTypes))
                    .content(new Content().addMediaType(PROBLEM_JSON, media));
            if (status == 429) {
                response.addHeaderObject("Retry-After", new Header()
                        .description("Segundos até a próxima tentativa ser aceita.")
                        .schema(new IntegerSchema()));
            }
            operation.getResponses().addApiResponse(String.valueOf(status), response);
        });
    }

    private static String description(int status, List<ProblemType> types) {
        var text = STATUS_TEXT.getOrDefault(status, "Erro");
        var lines = types.stream()
                .map(type -> "- `" + type.slug() + "`: " + type.title())
                .collect(Collectors.joining("\n"));
        return text + ". Tipos possíveis:\n\n" + lines;
    }

    private Map<String, Object> example(ProblemType type) {
        var body = new LinkedHashMap<String, Object>();
        body.put("type", problemsBaseUri + type.slug());
        body.put("title", type.title());
        body.put("status", type.status().value());
        body.put("detail", type.title());
        if (type == ProblemType.VALIDATION_FAILED) {
            body.put("detail", "Validation failed");
            body.put("errors", List.of(Map.of(
                    "field", "email",
                    "message", "must not be blank",
                    "code", "required",
                    "params", Map.of())));
        }
        if (type.status().is5xxServerError()) {
            body.put("requestId", "0f8e5a2c-3b1d-4c7e-9a6f-2d4b8c1e7f90");
        }
        return body;
    }
}
