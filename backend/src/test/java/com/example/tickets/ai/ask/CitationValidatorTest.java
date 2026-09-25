package com.example.tickets.ai.ask;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tickets.ai.ask.CitationValidator.Verdict;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CitationValidatorTest {

    private final CitationValidator validator = new CitationValidator(new ObjectMapper());
    private final Set<String> retrieved = Set.of("TKT-1001", "TKT-1004");

    @Test
    void shouldAcceptAnswerCitingOnlyRetrievedTickets() {
        Verdict v = validator.validate("""
                {"answerable": true, "answer": "Expired API key [TKT-1001].", "citedTicketIds": ["TKT-1001"]}""", retrieved);

        assertThat(v.grounded()).isTrue();
        assertThat(v.citedTicketIds()).containsExactly("TKT-1001");
        assertThat(v.answer()).isEqualTo("Expired API key [TKT-1001].");
    }

    @Test
    void shouldDropCitationsThatWereNotRetrieved() {
        Verdict v = validator.validate("""
                {"answerable": true, "answer": "Expired API key [TKT-1001].", "citedTicketIds": ["TKT-1001", "TKT-7777"]}""",
                retrieved);

        assertThat(v.grounded()).isTrue();
        assertThat(v.citedTicketIds()).containsExactly("TKT-1001");
    }

    @Test
    void shouldRejectAnswerMentioningAnUnretrievedTicket() {
        Verdict v = validator.validate("""
                {"answerable": true, "answer": "See [TKT-1001] and [TKT-9999].", "citedTicketIds": ["TKT-1001"]}""",
                retrieved);

        assertThat(v.grounded()).isFalse();
        assertThat(v.reason()).isEqualTo(NoMatchReason.UNVERIFIED_CITATIONS);
    }

    @Test
    void shouldRejectAnswerWithNoValidCitation() {
        Verdict v = validator.validate("""
                {"answerable": true, "answer": "Payment gateways often fail.", "citedTicketIds": []}""", retrieved);

        assertThat(v.grounded()).isFalse();
        assertThat(v.reason()).isEqualTo(NoMatchReason.UNVERIFIED_CITATIONS);
        assertThat(v.citedTicketIds()).isEmpty();
    }

    @Test
    void shouldReturnContextInsufficientWhenModelSaysNotAnswerable() {
        Verdict v = validator.validate("""
                {"answerable": false, "answer": "", "citedTicketIds": []}""", retrieved);

        assertThat(v.grounded()).isFalse();
        assertThat(v.reason()).isEqualTo(NoMatchReason.CONTEXT_INSUFFICIENT);
    }

    @Test
    void shouldTolerateCodeFencesButRejectInvalidJson() {
        assertThat(validator.validate("```json\n{\"answerable\": true, \"answer\": \"x [TKT-1004]\", "
                + "\"citedTicketIds\": [\"tkt-1004\"]}\n```", retrieved).grounded()).isTrue();
        assertThat(validator.validate("Sure! The answer is TKT-1001.", retrieved).reason())
                .isEqualTo(NoMatchReason.INVALID_MODEL_OUTPUT);
        assertThat(validator.validate("{\"answerable\": \"yes\"}", retrieved).reason())
                .isEqualTo(NoMatchReason.INVALID_MODEL_OUTPUT);
    }
}
