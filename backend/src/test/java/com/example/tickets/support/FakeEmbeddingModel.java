package com.example.tickets.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Deterministic embedding model for tests: hashed bag of words → 1536-dim unit vector. Texts sharing words
 * get a higher cosine similarity, so pgvector retrieval, filters and re-indexing can be tested for real
 * without calling OpenAI.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 1536;
    private static final Set<String> STOP_WORDS = Set.of("the", "a", "an", "of", "to", "in", "is", "are", "was",
            "were", "for", "on", "and", "or", "what", "have", "has", "we", "how", "do", "does", "did", "with", "at",
            "by", "it", "this", "that", "be", "i", "you", "our", "any", "from", "as", "me", "show", "which", "who",
            "there", "ever", "before", "about");

    private volatile boolean failing;

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        if (failing) {
            throw new IllegalStateException("embedding provider down (test)");
        }
        List<Embedding> embeddings = new ArrayList<>();
        int index = 0;
        for (String text : request.getInstructions()) {
            embeddings.add(new Embedding(vector(text), index++));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    static float[] vector(String text) {
        float[] v = new float[DIMENSIONS];
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() < 2 || STOP_WORDS.contains(token)) {
                continue;
            }
            v[Math.floorMod(stem(token).hashCode(), DIMENSIONS)] += 1f;
        }
        double norm = 0;
        for (float x : v) norm += x * x;
        if (norm == 0) {
            v[DIMENSIONS - 1] = 1f;
            return v;
        }
        float n = (float) Math.sqrt(norm);
        for (int i = 0; i < v.length; i++) v[i] /= n;
        return v;
    }

    private static String stem(String token) {
        return token.endsWith("s") && token.length() > 3 ? token.substring(0, token.length() - 1) : token;
    }
}
