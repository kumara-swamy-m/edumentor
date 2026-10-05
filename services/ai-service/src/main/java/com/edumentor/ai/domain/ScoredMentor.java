package com.edumentor.ai.domain;

/** {@code score} is cosine similarity in [0, 1]. It is a relevance score, not a probability. */
public record ScoredMentor(MentorDocument document, double score) {
}