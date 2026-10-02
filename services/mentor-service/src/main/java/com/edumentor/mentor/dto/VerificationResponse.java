package com.edumentor.mentor.dto;

import com.edumentor.mentor.entity.DocumentType;
import com.edumentor.mentor.entity.VerificationStatus;

import java.time.LocalDateTime;

public record VerificationResponse(
        Long id,
        DocumentType documentType,
        String documentReference,
        String note,
        VerificationStatus status,
        LocalDateTime submittedAt,
        Long reviewedBy,
        LocalDateTime reviewedAt
) {
}