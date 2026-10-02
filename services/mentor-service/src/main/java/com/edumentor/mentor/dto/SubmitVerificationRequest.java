package com.edumentor.mentor.dto;

import com.edumentor.mentor.entity.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitVerificationRequest(
        @NotNull(message = "Document type is required")
        DocumentType documentType,

        @NotBlank(message = "Document reference is required")
        @Size(max = 500, message = "Document reference must be at most 500 characters")
        String documentReference,

        @Size(max = 500, message = "Note must be at most 500 characters")
        String note
) {
}