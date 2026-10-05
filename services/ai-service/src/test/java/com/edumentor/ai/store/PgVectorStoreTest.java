package com.edumentor.ai.store;

import com.edumentor.ai.client.PublicMentor;
import com.edumentor.ai.config.AiProperties;
import com.edumentor.ai.domain.MentorDocument;
import com.edumentor.ai.domain.ScoredMentor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PgVectorStoreTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    private static PgVectorStore store;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() {
        String url = POSTGRES.getJdbcUrl() + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                + "stringtype=unspecified";
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
        store = new PgVectorStore(jdbc, new ObjectMapper(), new AiProperties("mock", 4, "pgvector", 10));
        store.initSchema();
        store.initSchema(); // idempotent
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM mentor_embeddings");
    }

    @Test
    void searchOrdersByCosineSimilarity() {
        store.upsert(doc(1, "KCET"), "m1", new float[]{1, 0, 0, 0});
        store.upsert(doc(2, "KCET"), "m1", new float[]{0.9f, 0.1f, 0, 0});
        store.upsert(doc(3, "KCET"), "m1", new float[]{0, 1, 0, 0});

        List<ScoredMentor> result = store.search(new float[]{1, 0, 0, 0}, "m1", null, 3);

        assertThat(result).extracting(s -> s.document().mentorId()).containsExactly(1L, 2L, 3L);
        assertThat(result.get(0).score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
        assertThat(result.get(2).score()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-4));
        assertThat(result.get(0).document().profile().name()).isEqualTo("Mentor 1");
    }

    @Test
    void examFilterAndLimitAreApplied() {
        store.upsert(doc(1, "KCET"), "m1", new float[]{1, 0, 0, 0});
        store.upsert(doc(2, "JEE"), "m1", new float[]{1, 0, 0, 0});
        store.upsert(doc(3, "KCET"), "m1", new float[]{0.5f, 0.5f, 0, 0});

        assertThat(store.search(new float[]{1, 0, 0, 0}, "m1", "JEE", 5))
                .extracting(s -> s.document().mentorId()).containsExactly(2L);
        assertThat(store.search(new float[]{1, 0, 0, 0}, "m1", "KCET", 1)).hasSize(1);
    }

    @Test
    void vectorsOfOtherModelsAreNeverCompared() {
        store.upsert(doc(1, "KCET"), "old-model", new float[]{1, 0, 0, 0});

        assertThat(store.search(new float[]{1, 0, 0, 0}, "new-model", null, 5)).isEmpty();
        assertThat(store.count("old-model")).isEqualTo(1);
        assertThat(store.count("new-model")).isZero();
    }

    @Test
    void upsertReplacesAndDeleteRemoves() {
        store.upsert(doc(1, "KCET"), "m1", new float[]{1, 0, 0, 0});
        store.upsert(doc(1, "KCET"), "m1", new float[]{0, 1, 0, 0});

        assertThat(store.allIds()).containsExactly(1L);
        assertThat(store.search(new float[]{0, 1, 0, 0}, "m1", null, 1).get(0).score())
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));

        store.delete(1L);
        assertThat(store.allIds()).isEmpty();
    }

    @Test
    void wrongDimensionIsRejectedBeforeReachingTheDatabase() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> store.upsert(doc(1, "KCET"), "m1", new float[]{1, 0}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MentorDocument doc(long id, String exam) {
        PublicMentor profile = new PublicMentor(id, "Mentor " + id, "RVCE", "B.E.", "CSE", 3, exam, 450, null,
                "Bengaluru", 4.5, 3, List.of("Branch selection"));
        return new MentorDocument(id, exam, "Exam: " + exam, profile);
    }
}