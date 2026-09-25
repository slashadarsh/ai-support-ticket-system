package com.example.tickets.ai.ask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder.Op;
import org.springframework.stereotype.Component;

/**
 * Deterministic query analysis (ADR-7): explicit constraints in the question become a metadata filter.
 * No LLM call is used to extract them (NFR-7).
 */
@Component
public class QueryAnalyzer {

    private static final Pattern TICKET_KEY = Pattern.compile("\\bTKT-\\d+\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HIGH_PRIORITY =
            Pattern.compile("\\b(high[- ]priority|urgent|critical)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESOLVED = Pattern.compile("\\b(resolved|fixed|closed)\\b", Pattern.CASE_INSENSITIVE);

    public QueryPlan analyze(String question) {
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = TICKET_KEY.matcher(question);
        while (matcher.find()) {
            keys.add(matcher.group().toUpperCase(Locale.ROOT));
        }
        return new QueryPlan(List.copyOf(keys), HIGH_PRIORITY.matcher(question).find(),
                RESOLVED.matcher(question).find());
    }

    public record QueryPlan(List<String> ticketKeys, boolean highPriorityOnly, boolean resolvedOnly) {

        /** R1: an explicit key is an exact lookup, so the similarity threshold is not applied. */
        public boolean exactLookup() {
            return !ticketKeys.isEmpty();
        }

        public Filter.Expression filter() {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            List<Op> parts = new ArrayList<>();
            if (!ticketKeys.isEmpty()) {
                parts.add(anyOf(b, "ticketId", ticketKeys));
            }
            if (highPriorityOnly) {
                parts.add(anyOf(b, "priority", List.of("HIGH", "CRITICAL")));
            }
            if (resolvedOnly) {
                parts.add(anyOf(b, "status", List.of("RESOLVED", "CLOSED")));
            }
            if (parts.isEmpty()) {
                return null;
            }
            Op combined = parts.get(0);
            for (int i = 1; i < parts.size(); i++) {
                combined = b.and(combined, parts.get(i));
            }
            return combined.build();
        }

        public String describe() {
            List<String> parts = new ArrayList<>();
            if (!ticketKeys.isEmpty()) parts.add("ticketId in " + ticketKeys);
            if (highPriorityOnly) parts.add("priority in [HIGH, CRITICAL]");
            if (resolvedOnly) parts.add("status in [RESOLVED, CLOSED]");
            return parts.isEmpty() ? null : String.join(" AND ", parts);
        }

        // OR of equalities rather than IN: supported by every Spring AI filter converter.
        private static Op anyOf(FilterExpressionBuilder b, String key, List<String> values) {
            Op op = b.eq(key, values.get(0));
            for (int i = 1; i < values.size(); i++) {
                op = b.or(op, b.eq(key, values.get(i)));
            }
            return op;
        }
    }
}
