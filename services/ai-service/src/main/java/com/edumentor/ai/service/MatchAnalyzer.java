package com.edumentor.ai.service;

import com.edumentor.ai.client.PublicMentor;
import com.edumentor.ai.domain.Candidate;
import com.edumentor.ai.domain.MatchFacts;
import com.edumentor.ai.dto.RecommendationRequest;
import com.edumentor.ai.text.TextTokens;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class MatchAnalyzer {

    public MatchFacts analyze(RecommendationRequest request, PublicMentor mentor) {
        boolean collegeMatch = request.preferredColleges().stream()
                .anyMatch(preferred -> containsEitherWay(mentor.college(), preferred));
        Set<String> mentorBranch = tokenSet(mentor.branch() + " " + mentor.course());
        boolean branchMatch = request.preferredBranches().stream()
                .anyMatch(preferred -> intersects(mentorBranch, tokenSet(preferred)));
        boolean locationMatch = request.location() != null && !request.location().isBlank()
                && containsEitherWay(mentor.location(), request.location());

        Set<String> wanted = tokenSet(String.join(" ", request.preferredBranches()) + " "
                + (request.goal() == null ? "" : request.goal()) + " " + request.exam());
        List<String> topics = new ArrayList<>();
        if (mentor.expertise() != null) {
            for (String topic : mentor.expertise()) {
                if (intersects(tokenSet(topic), wanted)) {
                    topics.add(topic);
                }
            }
        }
        return new MatchFacts(mentor.examPath(), collegeMatch, branchMatch, locationMatch, topics,
                mentor.college(), mentor.branch(), mentor.location(), mentor.rank());
    }

    /** Deterministic reason built only from the facts. Never mentions scores. */
    public String templateReason(Candidate candidate) {
        MatchFacts f = candidate.facts();
        List<String> parts = new ArrayList<>();
        parts.add("studies " + f.branch() + (f.branchMatch() ? " (your preferred branch)" : "")
                + " at " + f.college() + (f.collegeMatch() ? " (your preferred college)" : ""));
        if (f.locationMatch()) {
            parts.add("is based in " + f.location());
        }
        if (!f.matchedTopics().isEmpty()) {
            parts.add("has expertise in " + String.join(", ", f.matchedTopics()));
        }
        if (f.mentorRank() != null) {
            parts.add("secured rank " + f.mentorRank() + " in " + f.examPath());
        }
        return "Relevant for " + f.examPath() + " counselling: " + String.join(", ", parts) + ".";
    }

    private boolean containsEitherWay(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        String x = a.toLowerCase(Locale.ROOT).trim();
        String y = b.toLowerCase(Locale.ROOT).trim();
        return x.contains(y) || y.contains(x);
    }

    private Set<String> tokenSet(String text) {
        return new HashSet<>(TextTokens.tokens(text, true));
    }

    private boolean intersects(Set<String> a, Set<String> b) {
        return a.stream().anyMatch(b::contains);
    }
}