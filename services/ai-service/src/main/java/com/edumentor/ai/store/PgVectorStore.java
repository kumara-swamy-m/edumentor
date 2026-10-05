package com.edumentor.ai.store;

import com.edumentor.ai.client.PublicMentor;
import com.edumentor.ai.config.AiProperties;
import com.edumentor.ai.domain.MentorDocument;
import com.edumentor.ai.domain.ScoredMentor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.ai.vector-store", havingValue = "pgvector", matchIfMissing = true)
public class PgVectorStore implements VectorStore, ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final int dimensions;

    public PgVectorStore(JdbcTemplate jdbc, ObjectMapper objectMapper, AiProperties properties) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.dimensions = properties.dimensions();
    }

    @Override
    public void run(ApplicationArguments args) {
        initSchema();
    }

    /** Idempotent. The dimension comes from configuration (an int), never from user input. */
    public void initSchema() {
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mentor_embeddings ("
                + "mentor_id BIGINT PRIMARY KEY, "
                + "exam_path VARCHAR(10) NOT NULL, "
                + "content TEXT NOT NULL, "
                + "profile JSONB NOT NULL, "
                + "embedding_model VARCHAR(100) NOT NULL, "
                + "embedding vector(" + dimensions + ") NOT NULL, "
                + "updated_at TIMESTAMPTZ NOT NULL DEFAULT now())");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_mentor_embeddings_hnsw "
                + "ON mentor_embeddings USING hnsw (embedding vector_cosine_ops)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_mentor_embeddings_exam ON mentor_embeddings (exam_path)");
        log.info("pgvector schema ready: dimensions={}", dimensions);
    }

    @Override
    public void upsert(MentorDocument document, String model, float[] embedding) {
        if (embedding.length != dimensions) {
            throw new IllegalArgumentException(
                    "Expected " + dimensions + " dimensions but got " + embedding.length);
        }
        jdbc.update("INSERT INTO mentor_embeddings "
                        + "(mentor_id, exam_path, content, profile, embedding_model, embedding, updated_at) "
                        + "VALUES (?, ?, ?, ?::jsonb, ?, ?::vector, now()) "
                        + "ON CONFLICT (mentor_id) DO UPDATE SET exam_path = EXCLUDED.exam_path, "
                        + "content = EXCLUDED.content, profile = EXCLUDED.profile, "
                        + "embedding_model = EXCLUDED.embedding_model, embedding = EXCLUDED.embedding, "
                        + "updated_at = now()",
                document.mentorId(), document.examPath(), document.content(), toJson(document.profile()),
                model, VectorMath.toLiteral(embedding));
    }

    @Override
    public List<ScoredMentor> search(float[] query, String model, String examPath, int limit) {
        String literal = VectorMath.toLiteral(query);
        StringBuilder sql = new StringBuilder("SELECT mentor_id, exam_path, content, profile::text AS profile, "
                + "1 - (embedding <=> ?::vector) AS score FROM mentor_embeddings WHERE embedding_model = ?");
        List<Object> args = new ArrayList<>();
        args.add(literal);
        args.add(model);
        if (examPath != null) {
            sql.append(" AND exam_path = ?");
            args.add(examPath);
        }
        sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
        args.add(literal);
        args.add(limit);

        return jdbc.query(sql.toString(), (rs, rowNum) -> new ScoredMentor(
                new MentorDocument(rs.getLong("mentor_id"), rs.getString("exam_path"), rs.getString("content"),
                        fromJson(rs.getString("profile"))),
                VectorMath.clamp01(rs.getDouble("score"))), args.toArray());
    }

    @Override
    public void delete(Long mentorId) {
        jdbc.update("DELETE FROM mentor_embeddings WHERE mentor_id = ?", mentorId);
    }

    @Override
    public Set<Long> allIds() {
        return new HashSet<>(jdbc.queryForList("SELECT mentor_id FROM mentor_embeddings", Long.class));
    }

    @Override
    public long count(String model) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM mentor_embeddings WHERE embedding_model = ?",
                Long.class, model);
        return count == null ? 0 : count;
    }

    private String toJson(PublicMentor profile) {
        try {
            return objectMapper.writeValueAsString(profile);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize mentor profile", ex);
        }
    }

    private PublicMentor fromJson(String json) {
        try {
            return objectMapper.readValue(json, PublicMentor.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read stored mentor profile", ex);
        }
    }
}