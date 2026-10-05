package com.edumentor.ai.dto;

import com.edumentor.ai.domain.Exam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record RecommendationRequest(
        @NotNull(message = "Exam is required")
        Exam exam,

        @Schema(description = "Your rank or score. Only added to the search text; it does not change ranking rules.")
        @Positive(message = "Rank must be positive")
        Integer rank,

        @Size(max = 5, message = "At most 5 preferred colleges")
        List<@NotBlank(message = "College must not be blank")
        @Size(max = 100, message = "College must be at most 100 characters") String> preferredColleges,

        @Size(max = 5, message = "At most 5 preferred branches")
        List<@NotBlank(message = "Branch must not be blank")
        @Size(max = 100, message = "Branch must be at most 100 characters") String> preferredBranches,

        @Size(max = 100, message = "Location must be at most 100 characters")
        String location,

        @Schema(description = "Accepted but not used: prices belong to slots in booking-service, not to mentors.")
        @DecimalMin(value = "0", message = "Budget must not be negative")
        BigDecimal maxBudget,

        @Size(max = 500, message = "Goal must be at most 500 characters")
        String goal,

        @Min(value = 1, message = "Limit must be between 1 and 10")
        @Max(value = 10, message = "Limit must be between 1 and 10")
        Integer limit
) {

    public RecommendationRequest {
        preferredColleges = preferredColleges == null ? List.of()
                : preferredColleges.stream().filter(Objects::nonNull).toList();
        preferredBranches = preferredBranches == null ? List.of()
                : preferredBranches.stream().filter(Objects::nonNull).toList();
    }
}