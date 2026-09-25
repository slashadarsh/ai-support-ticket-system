package com.example.tickets.ai.ask;

import com.example.tickets.ai.ask.AskDtos.AskRequest;
import com.example.tickets.ai.ask.AskDtos.AskResponse;
import com.example.tickets.ai.ingest.IndexingCoordinator;
import com.example.tickets.ai.ingest.IndexingCoordinator.ReindexResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AskController {

    private final AskService askService;
    private final IndexingCoordinator indexing;

    public AskController(AskService askService, IndexingCoordinator indexing) {
        this.askService = askService;
        this.indexing = indexing;
    }

    @Operation(summary = "Answer a question from ticket history only, citing the tickets used")
    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return askService.ask(request.question());
    }

    @Operation(summary = "Re-index every ticket (recovery / after an embedding model change)")
    @PostMapping("/reindex")
    public ReindexResult reindex() {
        return indexing.reindexAll();
    }
}
