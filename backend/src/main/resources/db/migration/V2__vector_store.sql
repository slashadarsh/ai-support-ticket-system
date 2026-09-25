-- The extension itself is created once by a superuser during setup (pgvector is not a trusted
-- extension); IF NOT EXISTS makes this a no-op for the app role.
CREATE EXTENSION IF NOT EXISTS vector;

-- Layout expected by Spring AI PgVectorStore (initialize-schema=false, ADR-3).
CREATE TABLE vector_store (
    id        UUID PRIMARY KEY,
    content   TEXT,
    metadata  JSON,
    embedding VECTOR(1536)
);

CREATE INDEX ix_vector_store_embedding ON vector_store USING hnsw (embedding vector_cosine_ops);
