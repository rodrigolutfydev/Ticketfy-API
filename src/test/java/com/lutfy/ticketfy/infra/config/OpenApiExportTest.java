package com.lutfy.ticketfy.infra.config;

import com.lutfy.ticketfy.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OpenApiExportTest extends IntegrationTestBase {

    private static final Path OUTPUT = Path.of("docs", "openapi.json");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exportsTheOpenApiDocument() throws Exception {
        var body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        var document = mapper.readTree(body);

        assertThat(document.get("info").get("title").asString()).isEqualTo("Ticketfy API");
        assertThat(document.get("paths").has("/users/me/deletion")).isTrue();

        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, mapper.writeValueAsString(document).replace("\r\n", "\n") + "\n", StandardCharsets.UTF_8);
    }
}
