package com.example.tickets.ai.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Retrieval tuning (NFR-4). Never hardcoded: override with {@code app.rag.*} properties or
 * {@code APP_RAG_TOP_K} / {@code APP_RAG_SIMILARITY_THRESHOLD} environment variables.
 */
@Validated
@ConfigurationProperties("app.rag")
public record RagProperties(
        @DefaultValue("5") @Min(1) @Max(20) int topK,
        @DefaultValue("0.45") @DecimalMin("0.0") @DecimalMax("1.0") double similarityThreshold,
        @DefaultValue @Valid Chunk chunk) {

    public record Chunk(@DefaultValue("400") @Min(50) @Max(2000) int maxTokens) {
    }
}
