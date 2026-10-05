package com.edumentor.ai.service;

import com.edumentor.ai.client.PublicMentor;
import com.edumentor.ai.domain.MentorDocument;
import com.edumentor.ai.dto.RecommendationRequest;

import java.util.ArrayList;
import java.util.List;

/** Builds the texts that get embedded. Both sides use the same field names so they line up. */
public final class DocumentBuilder {

    private static final int MAX_BIO = 500;

    private DocumentBuilder() {
    }

    public static MentorDocument document(PublicMentor m) {
        List<String> parts = new ArrayList<>();
        parts.add("Exam: " + clean(m.examPath()));
        parts.add("College: " + clean(m.college()));
        parts.add("Course: " + clean(m.course()));
        parts.add("Branch: " + clean(m.branch()));
        parts.add("Location: " + clean(m.location()));
        if (m.year() != null) {
            parts.add("Year of study: " + m.year());
        }
        if (m.rank() != null) {
            parts.add("Mentor rank: " + m.rank());
        }
        if (m.expertise() != null && !m.expertise().isEmpty()) {
            parts.add("Expertise: " + clean(String.join(", ", m.expertise())));
        }
        if (m.bio() != null && !m.bio().isBlank()) {
            String bio = clean(m.bio());
            parts.add("About: " + (bio.length() > MAX_BIO ? bio.substring(0, MAX_BIO) : bio));
        }
        return new MentorDocument(m.id(), m.examPath(), String.join(". ", parts), m);
    }

    public static String queryText(RecommendationRequest r) {
        List<String> parts = new ArrayList<>();
        parts.add("Exam: " + r.exam());
        if (r.rank() != null) {
            parts.add("Rank: " + r.rank());
        }
        if (!r.preferredColleges().isEmpty()) {
            parts.add("Preferred colleges: " + clean(String.join(", ", r.preferredColleges())));
        }
        if (!r.preferredBranches().isEmpty()) {
            parts.add("Preferred branches: " + clean(String.join(", ", r.preferredBranches())));
        }
        if (r.location() != null && !r.location().isBlank()) {
            parts.add("Location: " + clean(r.location()));
        }
        if (r.goal() != null && !r.goal().isBlank()) {
            parts.add("Goal: " + clean(r.goal()));
        }
        return String.join(". ", parts);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip();
    }
}