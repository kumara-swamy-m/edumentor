package com.edumentor.mentor.dto;

import com.edumentor.mentor.entity.Exam;
import com.edumentor.mentor.entity.VerificationStatus;

import java.time.LocalDateTime;
import java.util.List;

public record MentorResponse(
        Long id,
        Long userId,
        String name,
        String college,
        String course,
        String branch,
        Integer year,
        Exam examPath,
        Integer rank,
        String bio,
        String location,
        VerificationStatus verificationStatus,
        String verificationNote,
        double rating,
        int reviewCount,
        List<String> expertise,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}