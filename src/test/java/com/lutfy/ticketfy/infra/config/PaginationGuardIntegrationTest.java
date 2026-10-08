package com.lutfy.ticketfy.infra.config;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PaginationGuardIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void unknownSortPropertyIsABadRequest() throws Exception {
        var bearer = "Bearer " + accessToken(userRepository.findById(insertUser("USER")).orElseThrow());

        mockMvc.perform(get("/events").param("sort", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value for parameter 'sort'"));
        mockMvc.perform(get("/orders").param("sort", "nope,desc").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/tickets/me").param("sort", "nope").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nestedSortPropertiesAreRejected() throws Exception {
        mockMvc.perform(get("/events").param("sort", "organizer.password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value for parameter 'sort'"));
        mockMvc.perform(get("/events").param("sort", "startsAt").param("sort", "organizer.email,desc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pageBeyondTheOffsetLimitIsABadRequest() throws Exception {
        var bearer = "Bearer " + accessToken(userRepository.findById(insertUser("ADMIN")).orElseThrow());

        mockMvc.perform(get("/events").param("page", "2147483647").param("size", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value for parameter 'page'"));
        mockMvc.perform(get("/events").param("page", "99999999999999999999999"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/admin/payouts").param("page", "21474837").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validPaginationStillWorks() throws Exception {
        var bearer = "Bearer " + accessToken(userRepository.findById(insertUser("USER")).orElseThrow());

        mockMvc.perform(get("/events").param("page", "21474836").param("size", "100").param("sort", "startsAt,desc"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/orders").param("sort", "createdAt,desc").header("Authorization", bearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/tickets/me").param("sort", "createdAt,desc").header("Authorization", bearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/events").param("page", "abc"))
                .andExpect(status().isOk());
    }
}
