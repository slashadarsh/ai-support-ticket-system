package com.example.tickets.ai.ask;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Builds the single chat request (ADR-5): a static system message first — identical on every call so the
 * provider can cache the prefix — then the delimited ticket context and the question.
 */
@Component
public class PromptFactory {

    private final String systemPrompt;

    public PromptFactory(@Value("classpath:prompts/ask-system.st") Resource systemPromptResource) {
        try {
            this.systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the assistant system prompt", e);
        }
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public Prompt build(String question, List<Document> retrieved) {
        return new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userMessage(question, retrieved))));
    }

    String userMessage(String question, List<Document> retrieved) {
        // Group chunks per ticket, keeping the best-scoring ticket first.
        Map<String, List<Document>> byTicket = new LinkedHashMap<>();
        for (Document doc : retrieved) {
            byTicket.computeIfAbsent(String.valueOf(doc.getMetadata().get("ticketId")), k -> new ArrayList<>()).add(doc);
        }
        StringBuilder sb = new StringBuilder("<context>\n");
        byTicket.forEach((ticketId, docs) -> {
            Map<String, Object> meta = docs.get(0).getMetadata();
            sb.append("<ticket id=\"").append(ticketId)
                    .append("\" status=\"").append(meta.get("status"))
                    .append("\" priority=\"").append(meta.get("priority"))
                    .append("\" category=\"").append(meta.get("category"))
                    .append("\">\n");
            for (Document doc : docs) {
                sb.append(neutralizeDelimiters(doc.getText())).append("\n\n");
            }
            sb.setLength(sb.length() - 1);
            sb.append("</ticket>\n");
        });
        sb.append("</context>\n").append("Question: ").append(question);
        return sb.toString();
    }

    /** Ticket text is untrusted: it must not be able to close or open context blocks (NFR-13). */
    static String neutralizeDelimiters(String text) {
        return text.replaceAll("(?i)<(/?)(ticket|context)", "&lt;$1$2");
    }
}
