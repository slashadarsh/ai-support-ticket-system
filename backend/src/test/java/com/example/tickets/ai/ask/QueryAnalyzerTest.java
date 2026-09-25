package com.example.tickets.ai.ask;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tickets.ai.ask.QueryAnalyzer.QueryPlan;
import org.junit.jupiter.api.Test;

class QueryAnalyzerTest {

    private final QueryAnalyzer analyzer = new QueryAnalyzer();

    @Test
    void shouldTurnTicketKeysIntoExactLookup() {
        QueryPlan plan = analyzer.analyze("What was the resolution for ticket tkt-1001?");

        assertThat(plan.ticketKeys()).containsExactly("TKT-1001");
        assertThat(plan.exactLookup()).isTrue();
        assertThat(plan.resolvedOnly()).isFalse(); // "resolution" is not "resolved"
        assertThat(plan.describe()).isEqualTo("ticketId in [TKT-1001]");
        assertThat(plan.filter()).isNotNull();
    }

    @Test
    void shouldDetectHighPriorityWording() {
        assertThat(analyzer.analyze("Which high-priority tickets are related to payment?").highPriorityOnly()).isTrue();
        assertThat(analyzer.analyze("Any high priority shipping issues?").highPriorityOnly()).isTrue();
        assertThat(analyzer.analyze("Show urgent tickets").highPriorityOnly()).isTrue();
        assertThat(analyzer.analyze("Have we seen payment failures before?").highPriorityOnly()).isFalse();
    }

    @Test
    void shouldDetectResolvedWordingOnWholeWordsOnly() {
        assertThat(analyzer.analyze("Show me similar resolved tickets").resolvedOnly()).isTrue();
        assertThat(analyzer.analyze("How was it fixed?").resolvedOnly()).isTrue();
        assertThat(analyzer.analyze("Which unresolved tickets mention refunds?").resolvedOnly()).isFalse();
    }

    @Test
    void shouldHaveNoFilterForPlainTopicQuestions() {
        QueryPlan plan = analyzer.analyze("What are the common causes of shipment tracking issues?");

        assertThat(plan.filter()).isNull();
        assertThat(plan.describe()).isNull();
        assertThat(plan.exactLookup()).isFalse();
    }

    @Test
    void shouldCombineRulesWithAnd() {
        QueryPlan plan = analyzer.analyze("Which critical payment tickets were resolved?");

        assertThat(plan.describe()).isEqualTo("priority in [HIGH, CRITICAL] AND status in [RESOLVED, CLOSED]");
    }
}
