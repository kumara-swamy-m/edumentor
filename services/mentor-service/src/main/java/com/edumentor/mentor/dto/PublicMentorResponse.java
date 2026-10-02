package com.edumentor.mentor.dto;

import com.edumentor.mentor.entity.Exam;

import java.util.List;

public record PublicMentorResponse(
        Long id,
        String name,
        String college,
        String course,
        String branch,
        Integer year,
        Exam examPath,
        Integer rank,
        String bio,
        String location,
        double rating,
        int reviewCount,
        List<String> expertise
) {
}