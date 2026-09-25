package com.example.tickets.ai.ask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tickets.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.retry.TransientAiException;

/** AC-16..AC-18, NFR-7, NFR-11 with a mocked ChatModel and real pgvector retrieval (FakeEmbeddingModel). */
class AskApiIT extends AbstractIntegrationTest {

    @BeforeEach
    void seedTickets() throws Exception {
        createTicket("Card payments failing at checkout", "Visa card payments declined by the payment gateway",
                "HIGH", "PAYMENT");
        createTicket("Shipment tracking stuck", "Carrier tracking page not updating after dispatch", "MEDIUM",
                "SHIPPING");
    }

    private static ChatResponse modelSays(String json) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

    private org.springframework.test.web.servlet.ResultActions ask(String question) throws Exception {
        return postJson("/api/ai/ask", Map.of("question", question));
    }

    @Test
    void returnsGroundedAnswerCitingRetrievedTicket() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(modelSays("""
                {"answerable": true, "answer": "Yes, Visa payments were declined [TKT-1001].", "citedTicketIds": ["TKT-1001"]}"""));

        ask("Have we seen card payment failures?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.citations[0].ticketId").value("TKT-1001"))
                .andExpect(jsonPath("$.citations[0].title").value("Card payments failing at checkout"))
                .andExpect(jsonPath("$.citations[0].status").value("OPEN"))
                .andExpect(jsonPath("$.noMatchReason").isEmpty())
                .andExpect(jsonPath("$.retrieval.matches[0].ticketId").value("TKT-1001"));
    }

    @Test
    void noRelevantTicketsNeverCallsTheLlm() throws Exception {
        ask("What is the capital of France?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(false))
                .andExpect(jsonPath("$.answer").value(AskDtos.NO_MATCH_MESSAGE))
                .andExpect(jsonPath("$.citations.length()").value(0))
                .andExpect(jsonPath("$.noMatchReason").value("NO_RELEVANT_TICKETS"))
                .andExpect(jsonPath("$.retrieval.matches.length()").value(0));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void unknownTicketKeyIsNoMatchWithoutLlmCall() throws Exception {
        ask("What was the resolution for ticket TKT-9999?")
                .andExpect(jsonPath("$.grounded").value(false))
                .andExpect(jsonPath("$.retrieval.filter").value("ticketId in [TKT-9999]"));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void exactKeyLookupBypassesTheThreshold() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(modelSays("""
                {"answerable": true, "answer": "It is still open [TKT-1002].", "citedTicketIds": ["TKT-1002"]}"""));

        ask("What was the resolution for ticket TKT-1002?")
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.retrieval.similarityThreshold").value(0.0))
                .andExpect(jsonPath("$.retrieval.matches[0].ticketId").value("TKT-1002"));
    }

    @Test
    void modelSayingNotAnswerableGivesNoMatch() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(modelSays("""
                {"answerable": false, "answer": "", "citedTicketIds": []}"""));

        ask("What is the refund policy for card payments?")
                .andExpect(jsonPath("$.grounded").value(false))
                .andExpect(jsonPath("$.noMatchReason").value("CONTEXT_INSUFFICIENT"))
                .andExpect(jsonPath("$.citations.length()").value(0));
    }

    @Test
    void hallucinatedCitationIsRejected() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(modelSays("""
                {"answerable": true, "answer": "Caused by expired certificates [TKT-4242].", "citedTicketIds": ["TKT-4242"]}"""));

        ask("Why did card payments fail?")
                .andExpect(jsonPath("$.grounded").value(false))
                .andExpect(jsonPath("$.noMatchReason").value("UNVERIFIED_CITATIONS"))
                .andExpect(jsonPath("$.citations.length()").value(0));
    }

    @Test
    void sendsExactlyOneChatCallWithStaticSystemPromptAndNoTools() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(modelSays("""
                {"answerable": true, "answer": "Declined [TKT-1001].", "citedTicketIds": ["TKT-1001"]}"""));

        ask("Why were card payments declined?");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(1)).call(captor.capture());
        Prompt prompt = captor.getValue();
        assertThat(prompt.getInstructions()).hasSize(2);
        assertThat(prompt.getInstructions().get(1).getText()).contains("<ticket id=\"TKT-1001\"");
        if (prompt.getOptions() instanceof ToolCallingChatOptions toolOptions) {
            assertThat(toolOptions.getToolCallbacks()).isEmpty();
            assertThat(toolOptions.getToolNames()).isEmpty();
        }
    }

    /** Regression for M-11: a ticket matched only through its comments must still reach the LLM with its resolution. */
    @Test
    void contextAlwaysIncludesTheSummaryOfEveryRetrievedTicket() throws Exception {
        String key = createTicket("Checkout anomaly", "Generic checkout anomaly report", "LOW", "OTHER");
        patchJson("/api/tickets/" + key, Map.of("resolutionNotes", "Added idempotency keys to payment requests."));
        postJson("/api/tickets/" + key + "/comments", Map.of("author", "Anita", "body", "Duplicate charges refunded twice"));
        when(chatModel.call(any(Prompt.class))).thenReturn(modelSays("""
                {"answerable": false, "answer": "", "citedTicketIds": []}"""));

        ask("duplicate charges refunded")
                .andExpect(jsonPath("$.retrieval.matches.length()").value(1))
                .andExpect(jsonPath("$.retrieval.matches[0].chunkType").value("COMMENTS"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        assertThat(captor.getValue().getInstructions().get(1).getText())
                .contains("Resolution: Added idempotency keys to payment requests.", "Duplicate charges refunded twice");
    }

    @Test
    void providerFailureReturns503() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenThrow(new TransientAiException("rate limited"));

        ask("Why were card payments declined?")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("https://tickets.example.com/problems/ai-unavailable"));
    }

    @Test
    void validatesQuestionBeforeAnyAiCall() throws Exception {
        ask("  ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("question"));
        ask(" ab ").andExpect(status().isBadRequest());
        ask("x".repeat(501)).andExpect(status().isBadRequest());
        postJson("/api/ai/ask", Map.of()).andExpect(status().isBadRequest());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void echoesConfiguredRetrievalParameters() throws Exception {
        ask("What is the capital of France?")
                .andExpect(jsonPath("$.retrieval.topK").value(5))
                .andExpect(jsonPath("$.retrieval.similarityThreshold").value(0.1));
    }
}
