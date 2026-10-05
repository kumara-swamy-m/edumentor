package com.edumentor.ai.store;

import com.edumentor.ai.domain.MentorDocument;
import com.edumentor.ai.domain.ScoredMentor;

import java.util.List;
import java.util.Set;

public interface VectorStore {

    /** Inserts or replaces the mentor's vector. {@code model} identifies which embedding model produced it. */
    void upsert(MentorDocument document, String model, float[] embedding);

    /** Nearest mentors by cosine similarity among vectors of the same model, optionally for one exam only. */
    List<ScoredMentor> search(float[] query, String model, String examPath, int limit);

    void delete(Long mentorId);

    Set<Long> allIds();

    long count(String model);
}