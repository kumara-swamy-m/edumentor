package com.edumentor.ai.domain;

public record Candidate(ScoredMentor scored, MatchFacts facts) {

    public Long mentorId() {
        return scored.document().mentorId();
    }
}