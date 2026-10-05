package com.edumentor.ai.client;

import java.util.List;

/** Mirrors mentor-service's PublicMentorResponse (approved mentors only). */
public record PublicMentor(
        Long id,
        String name,
        String college,
        String course,
        String branch,
        Integer year,
        String examPath,
        Integer rank,
        String bio,
        String location,
        double rating,
        int reviewCount,
        List<String> expertise
) {
}