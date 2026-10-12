package com.lutfy.ticketfy.infra.config;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OpenApiExportTest extends IntegrationTestBase {

    private static final Path OUTPUT = Path.of("docs", "openapi.json");
    private static final Path PROBLEM_TYPES = Path.of("docs", "problem-types.json");

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

    @Test
    void exportsTheProblemTypes() throws Exception {
        var types = Arrays.stream(ProblemType.values())
                .map(type -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("slug", type.slug());
                    entry.put("status", type.status().value());
                    entry.put("title", type.title());
                    return entry;
                })
                .toList();
        assertThat(types).extracting(entry -> entry.get("slug")).doesNotHaveDuplicates();

        var mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        Files.createDirectories(PROBLEM_TYPES.getParent());
        Files.writeString(PROBLEM_TYPES, mapper.writeValueAsString(types).replace("\r\n", "\n") + "\n",
                StandardCharsets.UTF_8);
    }
}
