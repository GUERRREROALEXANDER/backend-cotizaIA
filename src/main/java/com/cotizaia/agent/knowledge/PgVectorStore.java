package com.cotizaia.agent.knowledge;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * pgvector implementor: embeddings live in knowledge_vectors_pg.embedding and
 * the database does both filtering and ranking with its <=> distance operator,
 * so the cost stays index-shaped instead of linear in Java. The query vector
 * is always a bound parameter (a text literal cast to vector), never string
 * concatenation into the SQL.
 */
public class PgVectorStore implements VectorStore {

    private final JdbcTemplate jdbc;

    public PgVectorStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "pgvector";
    }

    @Override
    @Transactional
    public void upsert(List<VectorEntry> entries) {
        for (VectorEntry entry : entries) {
            jdbc.update("""
                    INSERT INTO knowledge_vectors_pg
                        (chunk_id, agency_id, document_id, kind, requirement_type, embedding)
                    VALUES (?, ?, ?, ?, ?, CAST(? AS vector))
                    ON CONFLICT (chunk_id) DO UPDATE SET
                        agency_id = EXCLUDED.agency_id,
                        document_id = EXCLUDED.document_id,
                        kind = EXCLUDED.kind,
                        requirement_type = EXCLUDED.requirement_type,
                        embedding = EXCLUDED.embedding
                    """, entry.chunkId(), entry.agencyId(), entry.documentId(), entry.kind().name(),
                    entry.requirementType(), toVectorLiteral(entry.vector()));
        }
    }

    @Override
    public List<VectorMatch> search(VectorQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT chunk_id, 1 - (embedding <=> CAST(? AS vector)) AS similarity
                FROM knowledge_vectors_pg
                WHERE agency_id = ? AND kind = ? AND vector_dims(embedding) = ?""");
        List<Object> args = new ArrayList<>();
        String literal = toVectorLiteral(query.vector());
        args.add(literal);
        args.add(query.agencyId());
        args.add(query.kind().name());
        // pgvector refuses to compare vectors of different dimensions (e.g. rows from a previous embedding model).
        args.add(query.vector().length);
        if (query.requirementType() != null) {
            sql.append(" AND requirement_type = ?");
            args.add(query.requirementType());
        }
        sql.append("\nORDER BY embedding <=> CAST(? AS vector)\nLIMIT ?");
        args.add(literal);
        args.add(query.k());
        return jdbc.query(sql.toString(),
                (rs, rowNum) -> new VectorMatch(rs.getLong("chunk_id"), rs.getDouble("similarity")), args.toArray());
    }

    @Override
    @Transactional
    public void deleteDocument(long agencyId, long documentId) {
        jdbc.update("DELETE FROM knowledge_vectors_pg WHERE agency_id = ? AND document_id = ?", agencyId, documentId);
    }

    /** pgvector text form: [0.1,0.2,...]; Float.toString keeps it exact and simple. */
    private static String toVectorLiteral(float[] vector) {
        StringBuilder literal = new StringBuilder(vector.length * 12 + 2).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(Float.toString(vector[i]));
        }
        return literal.append(']').toString();
    }
}
