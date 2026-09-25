# Graph Report - C2  (2026-09-25)

## Corpus Check
- Corpus is ~37,565 words - fits in a single context window. You may not need a graph.

## Summary
- 679 nodes · 1783 edges · 30 communities (26 shown, 4 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 152 edges (avg confidence: 0.83)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Shared DTOs And Exceptions
- API Integration Tests
- Seeding And Summary Lookup
- Ask And Reindex API
- ProblemDetail Error Handling
- App Bootstrap And Test Tooling
- Chunking Configuration
- State Machine Errors
- Frontend API Client
- Query Analysis And Indexing
- Fake Embedding Test Support
- Prompt And Seed Utilities
- Frontend Dependencies
- Ask Response Model
- Citation Validation
- Frontend Pages And Routing
- JPA Entity Mapping
- Prompt Construction
- Query Plan Rules
- TypeScript Config
- Ticket Entity
- Frontend Page Tests
- Ask Service Pipeline
- Frontend Test Dependencies
- Restart Persistence Test
- Priority Enum
- Comment Entity
- Category Enum
- Prompt Logging Hook
- Maven Project

## God Nodes (most connected - your core abstractions)
1. `Ticket` - 46 edges
2. `TicketStatus` - 35 edges
3. `AbstractIntegrationTest` - 29 edges
4. `TicketRepository` - 21 edges
5. `TicketService` - 21 edges
6. `RagEvaluationIT` - 21 edges
7. `AskService` - 20 edges
8. `Comment` - 20 edges
9. `Category` - 18 edges
10. `TicketResponse` - 18 edges

## Surprising Connections (you probably didn't know these)
- `AskController` --references--> `AskService`  [EXTRACTED]
  backend/src/main/java/com/example/tickets/ai/ask/AskController.java → backend/src/main/java/com/example/tickets/ai/ask/AskService.java
- `AskService` --references--> `CitationValidator`  [EXTRACTED]
  backend/src/main/java/com/example/tickets/ai/ask/AskService.java → backend/src/main/java/com/example/tickets/ai/ask/CitationValidator.java
- `AskService` --references--> `PromptFactory`  [EXTRACTED]
  backend/src/main/java/com/example/tickets/ai/ask/AskService.java → backend/src/main/java/com/example/tickets/ai/ask/PromptFactory.java
- `AskService` --references--> `QueryAnalyzer`  [EXTRACTED]
  backend/src/main/java/com/example/tickets/ai/ask/AskService.java → backend/src/main/java/com/example/tickets/ai/ask/QueryAnalyzer.java
- `AskService` --references--> `SummaryChunkLookup`  [EXTRACTED]
  backend/src/main/java/com/example/tickets/ai/ask/AskService.java → backend/src/main/java/com/example/tickets/ai/ask/SummaryChunkLookup.java

## Import Cycles
- None detected.

## Communities (30 total, 4 thin omitted)

### Community 0 - "Shared DTOs And Exceptions"
Cohesion: 0.06
Nodes (42): CommentRepository, ApiExceptions, FieldValidationException, FieldViolation, MalformedRequestException, ResolutionNotesRequiredException, TicketNotFoundException, PageResponse (+34 more)

### Community 1 - "API Integration Tests"
Cohesion: 0.09
Nodes (19): AskApiIT, ResultActions, IngestionIT, AbstractIntegrationTest, StateMachineIT, TicketApiIT, TicketStatusTest, com.fasterxml.jackson.databind.JsonNode (+11 more)

### Community 2 - "Seeding And Summary Lookup"
Cohesion: 0.09
Nodes (32): autowired, SummaryChunkLookup, SeedComment, SeedDataLoader, SeedTicket, Golden, RagEvaluationIT, TicketValidationWebTest (+24 more)

### Community 3 - "Ask And Reindex API"
Cohesion: 0.10
Nodes (17): AskController, AskRequest, IndexingCoordinator, ReindexResult, TicketIndexer, TicketRepository, org.springframework.ai.vectorstore.VectorStore, org.springframework.data.jpa.domain.Specification (+9 more)

### Community 4 - "ProblemDetail Error Handling"
Cohesion: 0.20
Nodes (18): arrays, GlobalExceptionHandler, com.fasterxml.jackson.databind.JsonMappingException, errorresponse, invalidformatexception, jakarta.servlet.http.HttpServletRequest, objects, org.springframework.http.converter.HttpMessageNotReadableException (+10 more)

### Community 5 - "App Bootstrap And Test Tooling"
Cohesion: 0.09
Nodes (23): any, argumentcaptor, assertthatthrownby, assistantmessage, atomicinteger, SupportTicketsApplication, RagPropertiesIT, ProviderRetryIT (+15 more)

### Community 6 - "Chunking Configuration"
Cohesion: 0.13
Nodes (12): Chunk, RagProperties, TicketChunker, TicketChunkerTest, decimalmax, decimalmin, defaultvalue, max (+4 more)

### Community 7 - "State Machine Errors"
Cohesion: 0.11
Nodes (14): InvalidStatusTransitionException, TicketClosedException, TicketStatus, CANCELLED, CLOSED, IN_PROGRESS, OPEN, RESOLVED (+6 more)

### Community 8 - "Frontend API Client"
Cohesion: 0.19
Nodes (18): api, ApiError, AskResponse, CATEGORIES, Category, Citation, Comment, CreateTicketBody (+10 more)

### Community 9 - "Query Analysis And Indexing"
Cohesion: 0.16
Nodes (18): arraylist, QueryAnalyzer, collections, enummap, enumset, filter, java.util.regex.Pattern, list (+10 more)

### Community 10 - "Fake Embedding Test Support"
Cohesion: 0.14
Nodes (12): FakeEmbeddingModel, TestAiConfig, embedding, mockito, org.springframework.ai.chat.model.ChatModel, org.springframework.ai.embedding.EmbeddingModel, org.springframework.ai.embedding.EmbeddingRequest, org.springframework.ai.embedding.EmbeddingResponse (+4 more)

### Community 11 - "Prompt And Seed Utilities"
Cohesion: 0.11
Nodes (17): comparator, datetimeformatter, inputstream, ioexception, linkedhashmap, org.springframework.ai.transformer.splitter.TokenTextSplitter, org.springframework.boot.context.event.ApplicationReadyEvent, org.springframework.context.event.EventListener (+9 more)

### Community 12 - "Frontend Dependencies"
Cohesion: 0.09
Nodes (20): dependencies, react, react-dom, react-router-dom, name, private, scripts, build (+12 more)

### Community 13 - "Ask Response Model"
Cohesion: 0.14
Nodes (16): AskDtos, AskResponse, Citation, Match, Retrieval, NoMatchReason, CONTEXT_INSUFFICIENT, INVALID_MODEL_OUTPUT (+8 more)

### Community 14 - "Citation Validation"
Cohesion: 0.18
Nodes (6): assertthat, CitationValidator, LlmAnswer, Verdict, CitationValidatorTest, objectmapper

### Community 15 - "Frontend Pages And Routing"
Cohesion: 0.18
Nodes (15): label(), Page, STATUSES, TicketSummary, App(), formatDate(), StatusBadge(), frontend_src_index (+7 more)

### Community 16 - "JPA Entity Mapping"
Cohesion: 0.16
Nodes (14): cascadetype, column, enumerated, enumtype, fetchtype, generatedvalue, generationtype, id (+6 more)

### Community 17 - "Prompt Construction"
Cohesion: 0.23
Nodes (5): PromptFactory, PromptFactoryTest, classpathresource, messagetype, org.springframework.ai.chat.prompt.Prompt

### Community 18 - "Query Plan Rules"
Cohesion: 0.26
Nodes (4): QueryPlan, QueryAnalyzerTest, Expression, org.springframework.ai.vectorstore.filter.FilterExpressionBuilder.Op

### Community 19 - "TypeScript Config"
Cohesion: 0.13
Nodes (14): compilerOptions, isolatedModules, jsx, lib, module, moduleResolution, noEmit, noUnusedLocals (+6 more)

### Community 21 - "Frontend Page Tests"
Cohesion: 0.38
Nodes (8): retrieval, ticket, mockFetch(), problem(), renderAt(), @testing-library/react, @testing-library/user-event, vitest

### Community 22 - "Ask Service Pipeline"
Cohesion: 0.32
Nodes (3): AskService, AiUnavailableException, org.springframework.ai.document.Document

### Community 23 - "Frontend Test Dependencies"
Cohesion: 0.18
Nodes (11): devDependencies, jsdom, @testing-library/jest-dom, @testing-library/react, @testing-library/user-event, @types/react, @types/react-dom, typescript (+3 more)

### Community 24 - "Restart Persistence Test"
Cohesion: 0.22
Nodes (7): RestartIT, commentrequest, createticketrequest, jdbctemplate, org.springframework.context.ConfigurableApplicationContext, springapplicationbuilder, ticketresponse

### Community 25 - "Priority Enum"
Cohesion: 0.22
Nodes (6): Priority, CRITICAL, HIGH, LOW, MEDIUM, document

### Community 27 - "Category Enum"
Cohesion: 0.22
Nodes (6): Category, ACCOUNT, OTHER, PAYMENT, SHIPPING, TECHNICAL

## Knowledge Gaps
- **60 isolated node(s):** `log-prompt.sh script`, `com.example:support-tickets`, `NO_RELEVANT_TICKETS`, `CONTEXT_INSUFFICIENT`, `UNVERIFIED_CITATIONS` (+55 more)
  These have ≤1 connection - possible missing edges. (Counts symbols only; 167 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **4 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Ticket` connect `Ticket Entity` to `Shared DTOs And Exceptions`, `Ask And Reindex API`, `Chunking Configuration`, `State Machine Errors`, `Query Analysis And Indexing`, `Prompt And Seed Utilities`, `JPA Entity Mapping`, `Priority Enum`, `Comment Entity`, `Category Enum`?**
  _High betweenness centrality (0.073) - this node is a cross-community bridge._
- **Why does `TicketStatus` connect `State Machine Errors` to `Shared DTOs And Exceptions`, `API Integration Tests`, `Seeding And Summary Lookup`, `Ask And Reindex API`, `Query Analysis And Indexing`, `Prompt And Seed Utilities`, `Ticket Entity`, `Priority Enum`?**
  _High betweenness centrality (0.053) - this node is a cross-community bridge._
- **Why does `AbstractIntegrationTest` connect `API Integration Tests` to `Seeding And Summary Lookup`, `Fake Embedding Test Support`, `App Bootstrap And Test Tooling`?**
  _High betweenness centrality (0.032) - this node is a cross-community bridge._
- **What connects `log-prompt.sh script`, `com.example:support-tickets`, `NO_RELEVANT_TICKETS` to the rest of the system?**
  _60 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Shared DTOs And Exceptions` be split into smaller, more focused modules?**
  _Cohesion score 0.057729138166894664 - nodes in this community are weakly interconnected._
- **Should `API Integration Tests` be split into smaller, more focused modules?**
  _Cohesion score 0.08589411329137357 - nodes in this community are weakly interconnected._
- **Should `Seeding And Summary Lookup` be split into smaller, more focused modules?**
  _Cohesion score 0.08985507246376812 - nodes in this community are weakly interconnected._