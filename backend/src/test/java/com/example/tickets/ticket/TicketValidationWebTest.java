package com.example.tickets.ticket;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** AC-12: every §2.4 violation on create is a 400 with a field error, and the service is never reached. */
@WebMvcTest(TicketController.class)
class TicketValidationWebTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean TicketService service;

    static Stream<Arguments> violations() {
        return Stream.of(
                Arguments.of("title", null),
                Arguments.of("title", "   "),
                Arguments.of("title", "t".repeat(201)),
                Arguments.of("description", null),
                Arguments.of("description", ""),
                Arguments.of("description", "d".repeat(5001)),
                Arguments.of("priority", null),
                Arguments.of("priority", "URGENT"),
                Arguments.of("category", null),
                Arguments.of("category", "BILLING"),
                Arguments.of("assignee", "a".repeat(101)));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("violations")
    void rejectsInvalidField(String field, String value) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("title", "Valid title", "description", "Valid description",
                "priority", "LOW", "category", "OTHER"));
        body.put(field, value);

        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://tickets.example.com/problems/validation-error"))
                .andExpect(jsonPath("$.errors[0].field").value(field));
        verifyNoInteractions(service);
    }
}
