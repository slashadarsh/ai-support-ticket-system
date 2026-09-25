package com.example.tickets.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.tickets.ai.ask.QueryAnalyzer;
import com.example.tickets.ai.ask.QueryAnalyzer.QueryPlan;
import com.example.tickets.ai.config.RagProperties;
import com.example.tickets.seed.SeedDataLoader;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * RAG evaluation against the real OpenAI models (spec/evaluation-strategy.md). Excluded from `mvn test`;
 * run with `mvn -Prag-eval verify`. Writes target/rag-eval/{threshold-sweep.md, summary.md, results.json}.
 */
@Tag("rag-eval")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("rageval")
@TestMethodOrder(OrderAnnotation.class)
class RagEvaluationIT {

    record Golden(String id, String type, String question, List<String> expected, String filter,
                  Integer minCitations, String forbidden) {
    }

    private static final Path OUT = Path.of("target", "rag-eval");
    private static boolean seeded;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired SeedDataLoader seeder;
    @Autowired VectorStore vectorStore;
    @Autowired QueryAnalyzer queryAnalyzer;
    @Autowired RagProperties rag;

    @BeforeEach
    void seedOnce() throws Exception {
        if (!seeded) {
            jdbc.execute("TRUNCATE comment, ticket, vector_store RESTART IDENTITY CASCADE");
            jdbc.execute("ALTER SEQUENCE ticket_key_seq RESTART WITH 1001");
            seeder.seed();
            seeded = true;
            Files.createDirectories(OUT);
        }
        Integer indexed = jdbc.queryForObject(
                "select count(distinct metadata::jsonb ->> 'ticketId') from vector_store", Integer.class);
        assertThat(indexed).as("all seed tickets indexed").isEqualTo(30);
    }

    private List<Golden> golden() throws Exception {
        try (InputStream in = new ClassPathResource("rag-eval/golden-questions.json").getInputStream()) {
            return json.readValue(in, new TypeReference<>() { });
        }
    }

    @Test
    @Order(1)
    void thresholdSweep() throws Exception {
        List<Golden> questions = golden();
        StringBuilder md = new StringBuilder("| threshold | hit@K (G1–G9) | N-questions with ≥1 chunk (of 6) |\n|---|---|---|\n");
        for (int t = 20; t <= 60; t += 5) {
            double threshold = t / 100.0;
            int hits = 0, inScope = 0, leaking = 0;
            for (Golden g : questions) {
                QueryPlan plan = queryAnalyzer.analyze(g.question());
                SearchRequest.Builder request = SearchRequest.builder().query(g.question()).topK(rag.topK())
                        .similarityThreshold(plan.exactLookup() ? 0.0 : threshold);
                if (plan.filter() != null) request.filterExpression(plan.filter());
                List<Document> docs = vectorStore.similaritySearch(request.build());
                Set<String> found = new HashSet<>();
                docs.forEach(d -> found.add(String.valueOf(d.getMetadata().get("ticketId"))));
                if ("IN_SCOPE".equals(g.type())) {
                    inScope++;
                    if (g.expected().stream().anyMatch(found::contains)) hits++;
                } else if ("OUT_OF_SCOPE".equals(g.type()) && !found.isEmpty()) {
                    leaking++;
                }
            }
            md.append("| %.2f | %.2f | %d |%n".formatted(threshold, (double) hits / inScope, leaking));
        }
        Files.writeString(OUT.resolve("threshold-sweep.md"), md.toString());
        System.out.println("\n=== THRESHOLD SWEEP (topK=" + rag.topK() + ")\n" + md);
    }

    @Test
    @Order(2)
    void goldenQuestions() throws Exception {
        List<Golden> questions = golden();
        List<Map<String, Object>> results = new ArrayList<>();
        int inScope = 0, hits = 0, grounded = 0, citations = 0, validCitations = 0, filterChecks = 0, filterOk = 0,
                outOfScope = 0, noMatchOk = 0, injections = 0;
        List<String> failures = new ArrayList<>();
        StringBuilder md = new StringBuilder(
                "| ID | Question | grounded | reason | cited | retrieved (score) | verdict |\n|---|---|---|---|---|---|---|\n");

        for (Golden g : questions) {
            JsonNode r = json.readTree(mvc.perform(post("/api/ai/ask").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("question", g.question()))))
                    .andReturn().getResponse().getContentAsString());
            boolean isGrounded = r.get("grounded").asBoolean();
            List<String> cited = r.get("citations").findValuesAsText("ticketId");
            Map<String, Double> matches = new LinkedHashMap<>();
            r.get("retrieval").get("matches").forEach(m -> matches.merge(m.get("ticketId").asText(),
                    m.get("score").asDouble(), Math::max));
            String answer = r.get("answer").asText();
            List<String> problems = new ArrayList<>();

            if (isGrounded) {
                citations += cited.size();
                validCitations += (int) cited.stream().filter(matches::containsKey).count();
            }
            switch (g.type()) {
                case "IN_SCOPE" -> {
                    inScope++;
                    if (g.expected().stream().anyMatch(matches::containsKey)) hits++; else problems.add("miss@K");
                    if (isGrounded) grounded++; else problems.add("not grounded");
                    if (g.minCitations() != null && cited.size() < g.minCitations()) problems.add("too few citations");
                    if (g.forbidden() != null && answer.contains(g.forbidden())) { injections++; problems.add("INJECTION FOLLOWED"); }
                    if (g.filter() != null) {
                        filterChecks++;
                        if (matches.keySet().stream().allMatch(k -> satisfies(g.filter(), k, g))) filterOk++;
                        else problems.add("filter violated");
                    }
                }
                case "OUT_OF_SCOPE" -> {
                    outOfScope++;
                    if (!isGrounded && cited.isEmpty()) noMatchOk++; else problems.add("FABRICATED ANSWER");
                }
                default -> { // AMBIGUOUS: no-match, or grounded with only resolved/closed tickets
                    if (isGrounded && !cited.stream().allMatch(k -> status(k).matches("RESOLVED|CLOSED"))) {
                        problems.add("cited unresolved ticket");
                    }
                }
            }
            String verdict = problems.isEmpty() ? "PASS" : "FAIL: " + String.join(", ", problems);
            if (!problems.isEmpty()) failures.add(g.id() + " " + verdict);
            md.append("| %s | %s | %s | %s | %s | %s | %s |%n".formatted(g.id(), g.question(), isGrounded,
                    r.get("noMatchReason").isNull() ? "" : r.get("noMatchReason").asText(), cited,
                    matches.entrySet().stream().map(e -> e.getKey() + " (" + e.getValue() + ")").toList(), verdict));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", g.id());
            row.put("response", r);
            row.put("verdict", verdict);
            results.add(row);
        }

        double hitAtK = (double) hits / inScope;
        double groundedRate = (double) grounded / inScope;
        double citationPrecision = citations == 0 ? 1.0 : (double) validCitations / citations;
        double filterCorrectness = filterChecks == 0 ? 1.0 : (double) filterOk / filterChecks;
        double noMatchAccuracy = (double) noMatchOk / outOfScope;
        md.append("""

                | Metric | Value | Threshold |
                |---|---|---|
                | hit@K | %.2f | ≥ 0.80 |
                | grounded rate | %.2f | ≥ 0.80 |
                | citation precision | %.2f | 1.00 |
                | filter correctness | %.2f | 1.00 |
                | no-match accuracy | %.2f | 1.00 |
                | injections followed | %d | 0 |

                top-K = %d, similarity threshold = %.2f
                """.formatted(hitAtK, groundedRate, citationPrecision, filterCorrectness, noMatchAccuracy, injections,
                rag.topK(), rag.similarityThreshold()));
        Files.writeString(OUT.resolve("summary.md"), md.toString());
        Files.writeString(OUT.resolve("results.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(results));
        System.out.println("\n=== RAG EVALUATION\n" + md + "\nFailures: " + failures);

        assertThat(hitAtK).as("hit@K").isGreaterThanOrEqualTo(0.8);
        assertThat(groundedRate).as("grounded rate").isGreaterThanOrEqualTo(0.8);
        assertThat(citationPrecision).as("citation precision").isEqualTo(1.0);
        assertThat(filterCorrectness).as("filter correctness").isEqualTo(1.0);
        assertThat(noMatchAccuracy).as("no-match accuracy").isEqualTo(1.0);
        assertThat(injections).as("prompt injections followed").isZero();
    }

    private boolean satisfies(String filter, String ticketKey, Golden g) {
        return switch (filter) {
            case "KEY" -> g.expected().contains(ticketKey);
            case "RESOLVED" -> status(ticketKey).matches("RESOLVED|CLOSED");
            case "HIGH_PRIORITY" -> jdbc.queryForObject("select priority from ticket where ticket_key = ?",
                    String.class, ticketKey).matches("HIGH|CRITICAL");
            default -> true;
        };
    }

    private String status(String ticketKey) {
        return jdbc.queryForObject("select status from ticket where ticket_key = ?", String.class, ticketKey);
    }
}
