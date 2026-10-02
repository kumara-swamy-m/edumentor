package com.edumentor.mentor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectMentorRequest(
        @NotBlank(message = "A rejection comment is required")
        @Size(max = 500, message = "Comment must be at most 500 characters")
        String comment
) {
}