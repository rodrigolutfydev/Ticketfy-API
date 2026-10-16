package com.lutfy.ticketfy.infra.config;

import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.infra.openapi.ProblemResponsesCustomizer;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    private static final String SITE = "https://ticketfy-6fd.pages.dev";

    private static final String DESCRIPTION = """
            API REST do **Ticketfy**, plataforma de venda de ingressos: vitrine de eventos, compra com cupom \
            e pagamento simulado, ingressos com código único, transferência, check-in, painel e saques do \
            organizador, administração e LGPD. Site: [ticketfy-6fd.pages.dev](%s).

            ### Autenticação
            `POST /auth/login` devolve um access token JWT (10 minutos) e grava o refresh token num cookie \
            `HttpOnly`. Envie o token em `Authorization: Bearer <token>` (no Swagger UI, botão **Authorize**). \
            Rotas sem cadeado são públicas. Rotas de organizador e de administração informam o papel exigido; \
            sem ele, a resposta é 403 `access-denied`.

            ### Erros
            Todo erro usa `application/problem+json` (RFC 9457, sucessor do RFC 7807). O campo `type` identifica \
            o erro e é estável; `detail` é texto livre em inglês. Cada rota lista os tipos que pode devolver, \
            e a lista completa, com os valores de `reason`, está em \
            [`docs/problem-types.json`](https://github.com/rodrigolutfydev/Ticketfy-API/blob/main/docs/problem-types.json). \
            Além dos listados, qualquer rota pode responder 405 `method-not-allowed` e, com corpo, \
            415 `unsupported-media-type`.

            ### Paginação
            Listagens paginadas aceitam `page` (começa em 0), `size` (padrão 20, máximo 100: valores maiores \
            são reduzidos para 100) e `sort` (`campo,asc` ou `campo,desc`, pode repetir). Campo de ordenação \
            inexistente responde 400 `invalid-parameter`. A resposta tem o formato:

            ```json
            { "content": [ ], "page": { "size": 20, "number": 0, "totalElements": 42, "totalPages": 3 } }
            ```

            ### Convenções
            Datas em ISO 8601 UTC (`2026-12-10T23:00:00Z`), dinheiro em reais com 2 casas (`80.00`) e \
            identificadores UUID. Cada resposta traz `X-Request-Id`, também presente no corpo dos erros 5xx.
            """.formatted(SITE);

    @Bean
    public OpenAPI ticketfyOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        var build = buildProperties.getIfAvailable();
        return new OpenAPI()
                .info(new Info()
                        .title("Ticketfy API")
                        .description(DESCRIPTION)
                        .version(build == null ? "dev" : build.getVersion())
                        .contact(new Contact()
                                .name("Rodrigo Lutfy")
                                .url("https://github.com/rodrigolutfydev/Ticketfy-API"))
                        .license(new License().name("MIT")
                                .url("https://github.com/rodrigolutfydev/Ticketfy-API/blob/main/LICENSE")))
                .externalDocs(new ExternalDocumentation().description("Site do Ticketfy").url(SITE))
                .servers(List.of(
                        new Server().url("https://ticketfy-api.onrender.com").description("Produção"),
                        new Server().url("http://localhost:8080").description("Local")))
                .tags(tags())
                .components(new Components()
                        .addSecuritySchemes(ProblemResponsesCustomizer.BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token devolvido por `POST /auth/login`, `/auth/google` ou "
                                        + "`/auth/refresh`. Vale 10 minutos."))
                        .addSchemas("ProblemDetail", problemDetailSchema())
                        .addSchemas("ValidationError", validationErrorSchema()));
    }

    // O springdoc descreve page, size, sort e o PageMetadata em inglês e sem os limites da API.
    @Bean
    public OpenApiCustomizer paginationAndHealthCustomizer(
            @Value("${spring.data.web.pageable.max-page-size}") int maxPageSize) {
        return openApi -> {
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                    describePageParameters(operation, maxPageSize)));
            var pageMetadata = openApi.getComponents().getSchemas().get("PageMetadata");
            if (pageMetadata != null) {
                pageMetadata.description("Dados da página devolvida.");
                describeProperty(pageMetadata, "size", "Tamanho da página pedida.");
                describeProperty(pageMetadata, "number", "Número da página, começando em 0.");
                describeProperty(pageMetadata, "totalElements", "Total de itens em todas as páginas.");
                describeProperty(pageMetadata, "totalPages", "Total de páginas.");
            }
            // Do actuator, só o health é público e interessa a quem lê o contrato.
            openApi.getPaths().keySet()
                    .removeIf(path -> path.startsWith("/actuator") && !path.equals("/actuator/health"));
            var health = openApi.getPaths().get("/actuator/health");
            if (health != null && health.getGet() != null) {
                describeHealth(health.getGet());
                openApi.getTags().removeIf(tag -> "Actuator".equals(tag.getName()));
            }
        };
    }

    private static List<Tag> tags() {
        return List.of(
                new Tag().name(ApiTags.AUTH).description("""
                        Login com senha ou Google, renovação da sessão, logout e recuperação de senha. Todo \
                        `POST /auth/**` exige os cabeçalhos `Origin` (uma origem liberada no CORS) e \
                        `X-Ticketfy-Auth: 1`; sem eles, a resposta é 403 `auth-request-rejected`. Por isso essas \
                        rotas não funcionam pelo "Try it out". O refresh token fica só no cookie \
                        `__Host-ticketfy_rt` (`HttpOnly`, `Secure`, `SameSite=Strict`, 30 minutos sem uso, \
                        sessão de no máximo 12 horas) e nunca aparece no corpo."""),
                new Tag().name(ApiTags.USERS).description(
                        "Cadastro, perfil, idioma, avatar, senha e pedido para virar organizador."),
                new Tag().name(ApiTags.EVENTS).description(
                        "Vitrine pública (busca, detalhe, categorias e agenda .ics) e gestão dos eventos."),
                new Tag().name(ApiTags.TICKET_TYPES).description(
                        "Lotes (tipos de ingresso) de cada evento, com preço, estoque e limites."),
                new Tag().name(ApiTags.ORDERS).description("""
                        Compra com reserva de estoque por 15 minutos, cupom opcional e `Idempotency-Key`, \
                        pagamento simulado, cancelamento e reembolso."""),
                new Tag().name(ApiTags.TICKETS).description(
                        "Ingressos do comprador, transferência, check-in na entrada e lista de participantes."),
                new Tag().name(ApiTags.COUPONS).description(
                        "Cupons de desconto por evento, geridos pelo organizador, e prévia do desconto na compra."),
                new Tag().name(ApiTags.ORGANIZER).description("""
                        Painéis de vendas, saldo, extrato, dados de recebimento (Pix) e saques. Saldo, extrato, \
                        dados de recebimento e saques são só do papel `ORGANIZER`."""),
                new Tag().name(ApiTags.ADMIN).description(
                        "Análise de saques, bloqueio de organizadores, suspensão de contas e auditoria."),
                new Tag().name(ApiTags.PRIVACY).description(
                        "Exportação dos dados pessoais e exclusão da conta, com confirmação de senha."),
                new Tag().name(ApiTags.SYSTEM).description("Verificação de saúde da API."));
    }

    private static Schema<?> problemDetailSchema() {
        return new ObjectSchema()
                .description("""
                        Erro no formato do RFC 9457 (antigo RFC 7807). Alguns tipos trazem campos extras, \
                        como `reason` (motivo estável dentro do tipo), `errors` (validação), `usedAt` \
                        (check-in repetido), `remaining`, `usesCount` ou `maxUsesPerBuyer`.""")
                .addProperty("type", new StringSchema().format("uri")
                        .description("Identificador estável do erro.")
                        .example("https://ticketfy-api.onrender.com/problems/event-not-found"))
                .addProperty("title", new StringSchema().description("Resumo do tipo, em inglês.")
                        .example("Event not found"))
                .addProperty("status", new IntegerSchema().description("Status HTTP.").example(404))
                .addProperty("detail", new StringSchema().description("Explicação deste caso, em inglês.")
                        .example("Event not found"))
                .addProperty("instance", new StringSchema().format("uri-reference")
                        .description("Caminho da requisição.")
                        .example("/events/3f2b8c1e-7d4a-4e9b-a1c2-5d6e7f8a9b0c"))
                .addProperty("reason", new StringSchema()
                        .description("Motivo, quando o tipo tem mais de um. Valores em `docs/problem-types.json`.")
                        .example("cancelled"))
                .addProperty("errors", new ArraySchema()
                        .items(new Schema<>().$ref("#/components/schemas/ValidationError"))
                        .description("Só em `validation-failed`: um item por campo inválido."))
                .addProperty("requestId", new StringSchema()
                        .description("Só em erros 5xx: o mesmo valor do cabeçalho `X-Request-Id`."))
                .required(List.of("type", "title", "status", "detail"));
    }

    private static Schema<?> validationErrorSchema() {
        return new ObjectSchema()
                .description("Campo que não passou na validação.")
                .addProperty("field", new StringSchema().example("email"))
                .addProperty("message", new StringSchema().description("Mensagem em inglês.")
                        .example("must not be blank"))
                .addProperty("code", new StringSchema().description("Código estável para o front traduzir.")
                        .example("required"))
                .addProperty("params", new ObjectSchema().description("Parâmetros do código, como `min` e `max`.")
                        .example(Map.of()));
    }

    private static void describePageParameters(Operation operation, int maxPageSize) {
        if (operation.getParameters() == null) {
            return;
        }
        operation.getParameters().stream()
                .filter(parameter -> "query".equals(parameter.getIn()))
                .forEach(parameter -> {
                    switch (parameter.getName()) {
                        case "page" -> parameter.description("Página, começando em 0.");
                        case "size" -> parameter.description("Itens por página. Máximo " + maxPageSize
                                + "; valores maiores são reduzidos para " + maxPageSize + ".");
                        case "sort" -> parameter.description("Ordenação no formato `campo,asc` ou `campo,desc`; "
                                + "pode repetir. Campo inexistente responde 400 `invalid-parameter`.");
                        default -> {
                        }
                    }
                });
    }

    private static void describeProperty(Schema<?> schema, String property, String description) {
        var value = schema.getProperties() == null ? null : schema.getProperties().get(property);
        if (value != null) {
            value.description(description);
        }
    }

    private static void describeHealth(Operation operation) {
        operation.tags(List.of(ApiTags.SYSTEM))
                .summary("Verificar a saúde da API")
                .description("Responde `UP` quando a API e o banco estão no ar.")
                .security(null)
                .responses(new ApiResponses()
                        .addApiResponse("200", healthResponse("API no ar.", "UP"))
                        .addApiResponse("503", healthResponse("API ou banco fora do ar.", "DOWN")));
    }

    private static ApiResponse healthResponse(String description, String status) {
        var schema = new ObjectSchema().addProperty("status", new StringSchema().example(status));
        var media = new MediaType().schema(schema);
        return new ApiResponse().description(description)
                .content(new Content()
                        .addMediaType("application/vnd.spring-boot.actuator.v3+json", media)
                        .addMediaType("application/json", media));
    }
}
