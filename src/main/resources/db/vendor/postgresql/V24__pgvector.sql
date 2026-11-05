-- V24__pgvector.sql (PostgreSQL vendor migration)
-- Accelerated copy of knowledge_vectors. Same kernel metadata columns, but the
-- embedding lives in a pgvector column with no fixed dimension so stub (256)
-- and Voyage (1024) models comingle; similarity search happens in the index
-- with the <=> operator instead of exact cosine in Java.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_vectors_pg (
    chunk_id         BIGINT PRIMARY KEY,
    agency_id        BIGINT NOT NULL,
    document_id      BIGINT NOT NULL,
    kind             VARCHAR(20) NOT NULL,
    requirement_type VARCHAR(255),
    embedding        vector NOT NULL,
    CONSTRAINT fk_knowledge_vectors_pg_chunk FOREIGN KEY (chunk_id)
        REFERENCES knowledge_chunks (id) ON DELETE CASCADE
);

CREATE INDEX idx_knowledge_vectors_pg_agency_kind ON knowledge_vectors_pg (agency_id, kind);
