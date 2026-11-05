-- V24__pgvector.sql (H2 vendor migration)
-- H2 has no pgvector extension or vector type, so the accelerated store is
-- unavailable there (the portable knowledge_vectors table and JdbcVectorStore
-- cover knowledge retrieval in dev/tests). This no-op keeps vendor migration
-- versions aligned with the PostgreSQL one.
SELECT 1;
