package com.example.tickets.ai.ask;

import com.example.tickets.ai.ask.AskDtos.AskResponse;
import com.example.tickets.ai.ask.AskDtos.Citation;
import com.example.tickets.ai.ask.AskDtos.Match;
import com.example.tickets.ai.ask.AskDtos.Retrieval;
import com.example.tickets.ai.ask.CitationValidator.Verdict;
import com.example.tickets.ai.ask.QueryAnalyzer.QueryPlan;
import com.example.tickets.ai.config.RagProperties;
import com.example.tickets.common.ApiExceptions.AiUnavailableException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * One question → one retrieval → at most one generation (NFR-7). Grounding is enforced in layers:
 * threshold gate, prompt, JSON contract, server-side citation check (ADR-8).
 */
@Service
public class AskService {

    private static final Logger log = LoggerFactory.getLogger(AskService.class);

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final QueryAnalyzer queryAnalyzer;
    private final PromptFactory promptFactory;
    private final CitationValidator citationValidator;
    private final SummaryChunkLookup summaries;
    private final RagProperties rag;

    public AskService(VectorStore vectorStore, ChatModel chatModel, QueryAnalyzer queryAnalyzer,
                      PromptFactory promptFactory, CitationValidator citationValidator,
                      SummaryChunkLookup summaries, RagProperties rag) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.queryAnalyzer = queryAnalyzer;
        this.promptFactory = promptFactory;
        this.citationValidator = citationValidator;
        this.summaries = summaries;
        this.rag = rag;
    }

    public AskResponse ask(String question) {
        QueryPlan plan = queryAnalyzer.analyze(question);
        double threshold = plan.exactLookup() ? 0.0 : rag.similarityThreshold();

        SearchRequest.Builder search = SearchRequest.builder()
                .query(question)
                .topK(rag.topK())
                .similarityThreshold(threshold);
        Filter.Expression filter = plan.filter();
        if (filter != null) {
            search.filterExpression(filter);
        }
        List<Document> retrieved = callProvider(() -> vectorStore.similaritySearch(search.build()));
        Retrieval retrieval = new Retrieval(rag.topK(), threshold, plan.describe(), matches(retrieved));

        if (retrieved.isEmpty()) {
            // FR-21a: nothing relevant → fixed answer, the LLM is never called.
            return noMatch(question, NoMatchReason.NO_RELEVANT_TICKETS, retrieval);
        }

        List<Document> context = withSummaries(retrieved);
        ChatResponse response = callProvider(() -> chatModel.call(promptFactory.build(question, context)));
        logUsage(response);
        String output = response.getResult() == null ? null : response.getResult().getOutput().getText();

        Map<String, Document> firstChunkPerTicket = new LinkedHashMap<>();
        retrieved.forEach(d -> firstChunkPerTicket.putIfAbsent(ticketId(d), d));
        Set<String> retrievedIds = new LinkedHashSet<>(firstChunkPerTicket.keySet());

        Verdict verdict = citationValidator.validate(output, retrievedIds);
        if (!verdict.grounded()) {
            return noMatch(question, verdict.reason(), retrieval);
        }
        List<Citation> citations = verdict.citedTicketIds().stream()
                .map(id -> {
                    Map<String, Object> meta = firstChunkPerTicket.get(id).getMetadata();
                    return new Citation(id, String.valueOf(meta.get("title")), String.valueOf(meta.get("status")));
                })
                .toList();
        return new AskResponse(question, true, verdict.answer(), citations, null, retrieval);
    }

    /**
     * Retrieval is per chunk, but a lone COMMENTS chunk hides the ticket's problem and resolution (M-11). The
     * context therefore always carries each retrieved ticket's SUMMARY chunk(s) first, then its matched chunks.
     */
    private List<Document> withSummaries(List<Document> retrieved) {
        Map<String, List<Document>> byTicket = new LinkedHashMap<>();
        retrieved.forEach(d -> byTicket.computeIfAbsent(ticketId(d), k -> new ArrayList<>()));
        summaries.summariesOf(byTicket.keySet()).forEach(d -> byTicket.get(ticketId(d)).add(d));
        for (Document d : retrieved) {
            List<Document> chunks = byTicket.get(ticketId(d));
            if (chunks.stream().noneMatch(c -> c.getId().equals(d.getId()))) {
                chunks.add(d);
            }
        }
        return byTicket.values().stream().flatMap(List::stream).toList();
    }

    private static AskResponse noMatch(String question, NoMatchReason reason, Retrieval retrieval) {
        return new AskResponse(question, false, AskDtos.NO_MATCH_MESSAGE, List.of(), reason, retrieval);
    }

    private static List<Match> matches(List<Document> documents) {
        return documents.stream()
                .map(d -> new Match(ticketId(d), String.valueOf(d.getMetadata().get("chunkType")),
                        d.getScore() == null ? 0.0 : Math.round(d.getScore() * 1000) / 1000.0))
                .toList();
    }

    private static String ticketId(Document document) {
        return String.valueOf(document.getMetadata().get("ticketId"));
    }

    /** Provider failures (embedding or chat) become 503; ticket management is unaffected (NFR-11). */
    private static <T> T callProvider(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (TransientAiException | NonTransientAiException | RestClientException e) {
            throw new AiUnavailableException(e);
        }
    }

    private static void logUsage(ChatResponse response) {
        if (response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        Integer cached = null;
        if (usage.getNativeUsage() instanceof OpenAiApi.Usage openAi && openAi.promptTokensDetails() != null) {
            cached = openAi.promptTokensDetails().cachedTokens();
        }
        log.debug("chat usage promptTokens={} cachedTokens={} completionTokens={}",
                usage.getPromptTokens(), cached, usage.getCompletionTokens());
    }
}
