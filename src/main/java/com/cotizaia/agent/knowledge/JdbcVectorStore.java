package com.cotizaia.agent.knowledge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portable implementor: embeddings live in knowledge_vectors as little-endian
 * float bytes and similarity is exact cosine computed in Java over the
 * candidates of one agency/kind/requirement-type slice. Works on any database
 * with a binary column, including the H2 test profile.
 */
public class JdbcVectorStore implements VectorStore {

    private final JdbcTemplate jdbc;

    public JdbcVectorStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "jdbc";
    }

    @Override
    @Transactional
    public void upsert(List<VectorEntry> entries) {
        for (VectorEntry entry : entries) {
            // Delete-then-insert instead of a dialect-specific MERGE: the same
            // statement must run on H2 and PostgreSQL.
            jdbc.update("DELETE FROM knowledge_vectors WHERE chunk_id = ?", entry.chunkId());
            jdbc.update("""
                    INSERT INTO knowledge_vectors
                        (chunk_id, agency_id, document_id, kind, requirement_type, dims, vector)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, entry.chunkId(), entry.agencyId(), entry.documentId(), entry.kind().name(),
                    entry.requirementType(), entry.vector().length, encode(entry.vector()));
        }
    }

    @Override
    public List<VectorMatch> search(VectorQuery query) {
        float[] queryVector = query.vector();
        double queryNorm = norm(queryVector);
        List<Candidate> candidates = load(query);
        List<VectorMatch> matches = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            matches.add(new VectorMatch(candidate.chunkId(), cosine(candidate.vector(), queryVector, queryNorm)));
        }
        matches.sort(Comparator.comparingDouble(VectorMatch::similarity).reversed()
                .thenComparing(VectorMatch::chunkId));
        return matches.subList(0, Math.min(query.k(), matches.size()));
    }

    @Override
    @Transactional
    public void deleteDocument(long agencyId, long documentId) {
        jdbc.update("DELETE FROM knowledge_vectors WHERE agency_id = ? AND document_id = ?", agencyId, documentId);
    }

    /** Loads one candidate per row of the agency/kind/requirement-type slice. */
    private List<Candidate> load(VectorQuery query) {
        StringBuilder sql = new StringBuilder(
                "SELECT chunk_id, vector FROM knowledge_vectors WHERE agency_id = ? AND kind = ?");
        List<Object> args = new ArrayList<>();
        args.add(query.agencyId());
        args.add(query.kind().name());
        if (query.requirementType() != null) {
            sql.append(" AND requirement_type = ?");
            args.add(query.requirementType());
        }
        return jdbc.query(sql.toString(),
                (rs, rowNum) -> new Candidate(rs.getLong("chunk_id"), decode(rs.getBytes("vector"))), args.toArray());
    }

    /** Candidate vector with its chunk id, already decoded to floats. */
    private record Candidate(long chunkId, float[] vector) {
    }

    /**
     * Cosine of a zero vector is undefined; it ranks last as 0. Vectors from another embedding model (other
     * dimensions, e.g. after switching stub -> Voyage) are not comparable and also rank as 0 instead of failing.
     */
    private double cosine(float[] candidate, float[] query, double queryNorm) {
        if (candidate.length != query.length) {
            return 0;
        }
        double dot = 0;
        double candidateNorm = 0;
        for (int i = 0; i < query.length; i++) {
            double value = candidate[i];
            dot += value * query[i];
            candidateNorm += value * value;
        }
        if (candidateNorm == 0 || queryNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(candidateNorm) * queryNorm);
    }

    private double norm(float[] vector) {
        double norm = 0;
        for (float value : vector) {
            norm += (double) value * value;
        }
        return Math.sqrt(norm);
    }

    static byte[] encode(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    static float[] decode(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / 4];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }
}
