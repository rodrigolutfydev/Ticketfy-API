package com.lutfy.ticketfy.infra.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI ticketfyOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        var build = buildProperties.getIfAvailable();
        return new OpenAPI()
                .info(new Info()
                        .title("Ticketfy API")
                        .description("API REST de venda, transferência e validação de ingressos para eventos. "
                                + "Referência completa em docs/API.md.")
                        .version(build == null ? "dev" : build.getVersion())
                        .license(new License().name("MIT")))
                .servers(List.of(new Server().url("https://ticketfy-api.onrender.com").description("Produção")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
