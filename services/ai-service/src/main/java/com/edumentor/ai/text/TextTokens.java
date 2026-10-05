package com.edumentor.ai.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TextTokens {

    private static final Set<String> STOP = Set.of(
            // field labels used in the generated profile and query texts
            "exam", "college", "colleges", "course", "branch", "branches", "location", "year", "study",
            "mentor", "rank", "expertise", "about", "goal", "preferred",
            // common English words
            "of", "and", "the", "in", "for", "with", "to", "a", "is", "my", "i", "should", "which", "are", "on");

    /** Domain words so common that sharing them says nothing (used for matching, not for embedding). */
    private static final Set<String> GENERIC = Set.of(
            "engineering", "technology", "science", "tech", "counselling", "counseling", "guidance", "be");

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("cs", "cse"), Map.entry("computer", "cse"), Map.entry("computers", "cse"),
            Map.entry("ec", "ece"), Map.entry("electronics", "ece"),
            Map.entry("electrical", "eee"),
            Map.entry("mechanical", "mech"),
            Map.entry("aiml", "ai"),
            Map.entry("medicine", "mbbs"));

    private TextTokens() {
    }

    public static List<String> tokens(String text, boolean dropGeneric) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        for (String raw : text.toLowerCase().split("[^\\p{L}\\p{Nd}]+")) {
            if (raw.length() < 2 || STOP.contains(raw) || raw.chars().allMatch(Character::isDigit)) {
                continue;
            }
            String token = ALIASES.getOrDefault(raw, raw);
            if (dropGeneric && GENERIC.contains(token)) {
                continue;
            }
            result.add(token);
        }
        return result;
    }
}