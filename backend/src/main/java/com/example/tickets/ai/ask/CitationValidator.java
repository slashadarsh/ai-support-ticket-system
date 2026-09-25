package com.example.tickets.ai.ask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Server-side grounding check (ADR-8, spec/rag-api-contract.md §3). The model's claims about which tickets
 * it used are verified against what was actually retrieved; anything unverifiable becomes a no-match.
 */
@Component
public class CitationValidator {

    private static final Logger log = LoggerFactory.getLogger(CitationValidator.class);
    private static final Pattern TICKET_KEY = Pattern.compile("\\bTKT-\\d+\\b", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    public CitationValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Verdict validate(String modelOutput, Set<String> retrievedTicketIds) {
        Optional<LlmAnswer> parsed = parse(modelOutput);
        if (parsed.isEmpty()) {
            log.warn("model output is not valid answer JSON: {}", abbreviate(modelOutput));
            return Verdict.noMatch(NoMatchReason.INVALID_MODEL_OUTPUT);
        }
        LlmAnswer answer = parsed.get();
        if (!answer.answerable()) {
            return Verdict.noMatch(NoMatchReason.CONTEXT_INSUFFICIENT);
        }

        List<String> valid = new ArrayList<>();
        for (String cited : answer.citedTicketIds()) {
            if (retrievedTicketIds.contains(cited)) {
                valid.add(cited);
            } else {
                log.warn("dropped citation {} that was not in the retrieved set {}", cited, retrievedTicketIds);
            }
        }

        Matcher inline = TICKET_KEY.matcher(answer.answer());
        while (inline.find()) {
            String key = inline.group().toUpperCase(Locale.ROOT);
            if (!retrievedTicketIds.contains(key)) {
                log.warn("answer mentions {} which was not retrieved; downgrading to no-match", key);
                return Verdict.noMatch(NoMatchReason.UNVERIFIED_CITATIONS);
            }
        }
        if (valid.isEmpty() || answer.answer().isBlank()) {
            return Verdict.noMatch(NoMatchReason.UNVERIFIED_CITATIONS);
        }
        return new Verdict(true, answer.answer().strip(), valid, null);
    }

    Optional<LlmAnswer> parse(String modelOutput) {
        if (modelOutput == null) {
            return Optional.empty();
        }
        int start = modelOutput.indexOf('{');
        int end = modelOutput.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(modelOutput.substring(start, end + 1));
            JsonNode answerable = node.get("answerable");
            JsonNode answer = node.get("answer");
            JsonNode cited = node.get("citedTicketIds");
            if (answerable == null || !answerable.isBoolean() || answer == null || !answer.isTextual()
                    || cited == null || !cited.isArray()) {
                return Optional.empty();
            }
            Set<String> ids = new LinkedHashSet<>();
            cited.forEach(id -> ids.add(id.asText().strip().toUpperCase(Locale.ROOT)));
            return Optional.of(new LlmAnswer(answerable.booleanValue(), answer.textValue(), List.copyOf(ids)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String abbreviate(String text) {
        if (text == null) return "null";
        return text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }

    record LlmAnswer(boolean answerable, String answer, List<String> citedTicketIds) {
    }

    public record Verdict(boolean grounded, String answer, List<String> citedTicketIds, NoMatchReason reason) {
        static Verdict noMatch(NoMatchReason reason) {
            return new Verdict(false, null, List.of(), reason);
        }
    }
}
