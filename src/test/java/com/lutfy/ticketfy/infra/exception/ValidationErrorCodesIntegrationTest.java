package com.lutfy.ticketfy.infra.exception;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ValidationErrorCodesIntegrationTest extends IntegrationTestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private String userToken;
    private String organizerToken;
    private UUID eventId;
    private UUID ticketTypeId;

    @BeforeEach
    void setUp() {
        var userId = insertUser("USER");
        var organizerId = insertUser("ORGANIZER");
        userToken = "Bearer " + accessToken(userRepository.findById(userId).orElseThrow());
        organizerToken = "Bearer " + accessToken(userRepository.findById(organizerId).orElseThrow());
        eventId = insertEvent(organizerId);
        ticketTypeId = insertTicketType(eventId, 100);
    }

    @Test
    void registrationErrorsCarryRequiredEmailAndSizeCodes() throws Exception {
        var errors = errors(HttpMethod.POST, "/users", null,
                "{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}");

        assertCode(errors, "name", "required");
        assertCode(errors, "email", "email");
        var password = assertCode(errors, "password", "size");
        assertThat(password.get("params").get("min").asInt()).isEqualTo(8);
        assertThat(password.get("params").get("max").asInt()).isEqualTo(72);
        assertThat(password.get("message").asString()).isEqualTo("Password must be at least 8 characters long");
    }

    @Test
    void sizeWithOnlyMaxOmitsMin() throws Exception {
        var errors = errors(HttpMethod.POST, "/events", organizerToken, """
                {"name":"%s","venueName":"Arena","address":"Rua A, 1","city":"Rio","state":"RJ",
                 "startsAt":"2030-01-01T20:00:00Z","category":"MUSIC"}
                """.formatted("x".repeat(151)));

        var size = assertCode(errors, "name", "size");
        assertThat(size.get("params").has("min")).isFalse();
        assertThat(size.get("params").get("max").asInt()).isEqualTo(150);
    }

    @Test
    void emptyItemListUsesCountAndNestedFieldsUsePositive() throws Exception {
        var empty = errors(HttpMethod.POST, "/orders", userToken, "{\"items\":[]}");
        var items = assertCode(empty, "items", "count");
        assertThat(items.get("params").get("min").asInt()).isEqualTo(1);

        var nested = errors(HttpMethod.POST, "/orders", userToken,
                "{\"items\":[{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":0}]}");
        assertCode(nested, "items[0].quantity", "positive");
    }

    @Test
    void couponErrorsCarryCouponCodePositiveAndDigits() throws Exception {
        var invalidCode = errors(HttpMethod.POST, "/events/" + eventId + "/coupons", organizerToken,
                "{\"code\":\"a!\",\"discountType\":\"FIXED\",\"discountValue\":-1}");
        var code = assertCode(invalidCode, "code", "coupon-code");
        assertThat(code.get("params").get("min").asInt()).isEqualTo(3);
        assertThat(code.get("params").get("max").asInt()).isEqualTo(30);
        assertCode(invalidCode, "discountValue", "positive");

        var tooManyDigits = errors(HttpMethod.POST, "/events/" + eventId + "/coupons", organizerToken,
                "{\"code\":\"PROMO10\",\"discountType\":\"FIXED\",\"discountValue\":1.234}");
        var digits = assertCode(tooManyDigits, "discountValue", "digits");
        assertThat(digits.get("params").get("integer").asInt()).isEqualTo(8);
        assertThat(digits.get("params").get("fraction").asInt()).isEqualTo(2);
    }

    @Test
    void pastEventDateUsesFuture() throws Exception {
        var errors = errors(HttpMethod.POST, "/events", organizerToken, """
                {"name":"Show","venueName":"Arena","address":"Rua A, 1","city":"Rio","state":"RJ",
                 "startsAt":"2020-01-01T20:00:00Z","category":"MUSIC"}
                """);

        assertCode(errors, "startsAt", "future");
    }

    @Test
    void nonHttpsAvatarUsesUrlWithProtocol() throws Exception {
        var errors = errors(HttpMethod.PATCH, "/users/me/avatar", userToken,
                "{\"avatarUrl\":\"http://cdn.example.com/a.png\"}");

        var url = assertCode(errors, "avatarUrl", "url");
        assertThat(url.get("params").get("protocol").asString()).isEqualTo("https");
    }

    @Test
    void blankTicketTypeNameUsesRequired() throws Exception {
        var errors = errors(HttpMethod.PATCH, "/events/" + eventId + "/ticket-types/" + ticketTypeId, organizerToken,
                "{\"name\":\"   \"}");

        assertCode(errors, "name", "required");
    }

    @Test
    void wrongDeletionConfirmationUsesConfirmation() throws Exception {
        var errors = errors(HttpMethod.POST, "/users/me/deletion", userToken,
                "{\"password\":\"secret\",\"confirmation\":\"APAGAR\"}");

        var confirmation = assertCode(errors, "confirmation", "confirmation");
        assertThat(confirmation.get("params").get("allowed").valueStream().map(JsonNode::asString).toList())
                .containsExactly("EXCLUIR", "DELETE");
    }

    @Test
    void invalidDocumentAndPixKeyCarryTheirTypes() throws Exception {
        var errors = errors(HttpMethod.PUT, "/organizer/payout-account", organizerToken, """
                {"documentType":"CPF","document":"111.111.111-11","holderName":"Ana",
                 "pixKeyType":"EMAIL","pixKey":"not-an-email","password":"secret"}
                """);

        var document = assertCode(errors, "document", "invalid-document");
        assertThat(document.get("params").get("documentType").asString()).isEqualTo("CPF");
        var pixKey = assertCode(errors, "pixKey", "invalid-pix-key");
        assertThat(pixKey.get("params").get("pixKeyType").asString()).isEqualTo("EMAIL");
    }

    @Test
    void unsupportedLanguageUsesOneOf() throws Exception {
        var errors = errors(HttpMethod.PATCH, "/users/me/language", userToken, "{\"language\":\"es\"}");

        var language = assertCode(errors, "language", "one-of");
        assertThat(language.get("params").get("allowed").valueStream().map(JsonNode::asString).toList())
                .containsExactly("pt-BR", "en");
    }

    private JsonNode errors(HttpMethod method, String path, String token, String body) throws Exception {
        var builder = request(method, path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            builder.header("Authorization", token);
        }
        var response = mockMvc.perform(builder)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/validation-failed")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return MAPPER.readTree(response).get("errors");
    }

    private static JsonNode assertCode(JsonNode errors, String field, String code) {
        var error = find(errors, field, code);
        assertThat(error).as("%s with code %s in %s", field, code, errors).isNotNull();
        assertThat(error.get("message").asString()).isNotBlank();
        assertThat(error.get("params").isObject()).isTrue();
        return error;
    }

    private static JsonNode find(JsonNode errors, String field, String code) {
        for (var error : errors) {
            if (error.get("field").asString().equals(field) && error.get("code").asString().equals(code)) {
                return error;
            }
        }
        return null;
    }
}
