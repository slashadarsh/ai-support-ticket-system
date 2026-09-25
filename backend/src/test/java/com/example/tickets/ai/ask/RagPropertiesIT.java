package com.example.tickets.ai.ask;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.example.tickets.SupportTicketsApplication;
import com.example.tickets.support.AbstractIntegrationTest;
import com.example.tickets.support.TestAiConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.test.context.TestPropertySource;

/** AC-21: retrieval parameters come from configuration, not code. */
@TestPropertySource(properties = {"app.rag.top-k=2", "app.rag.similarity-threshold=0.0"})
class RagPropertiesIT extends AbstractIntegrationTest {

    @Test
    void usesConfiguredTopKAndThreshold() throws Exception {
        for (int i = 0; i < 4; i++) {
            createTicket("Payment problem " + i, "Payment declined case " + i, "LOW", "PAYMENT");
        }

        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(
                new AssistantMessage("{\"answerable\": false, \"answer\": \"\", \"citedTicketIds\": []}")))));

        // Every ticket shares a word with the question, so only top-K limits the result.
        postJson("/api/ai/ask", Map.of("question", "Any payment problem?"))
                .andExpect(jsonPath("$.retrieval.topK").value(2))
                .andExpect(jsonPath("$.retrieval.similarityThreshold").value(0.0))
                .andExpect(jsonPath("$.retrieval.matches.length()").value(2));
    }

    @Test
    void rejectsInvalidConfigurationAtStartup() {
        SpringApplicationBuilder app = new SpringApplicationBuilder(SupportTicketsApplication.class, TestAiConfig.class)
                .web(WebApplicationType.NONE)
                .profiles("test");

        // A command-line argument, not builder.properties(): those are defaults that application.yml overrides.
        assertThatThrownBy(() -> app.run("--app.rag.top-k=0"))
                .hasStackTraceContaining("app.rag")
                .hasStackTraceContaining("topK");
    }
}
