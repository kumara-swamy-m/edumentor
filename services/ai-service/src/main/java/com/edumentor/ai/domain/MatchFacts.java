package com.edumentor.ai.domain;

import java.util.List;

/** Verifiable overlaps between a student's request and a mentor. Everything in an explanation comes from here. */
public record MatchFacts(
        String examPath,
        boolean collegeMatch,
        boolean branchMatch,
        boolean locationMatch,
        List<String> matchedTopics,
        String college,
        String branch,
        String location,
        Integer mentorRank
) {
}