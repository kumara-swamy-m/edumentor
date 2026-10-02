package com.edumentor.mentor.dto;

import com.edumentor.mentor.entity.Exam;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MentorProfileRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotBlank(message = "College is required")
        @Size(max = 150, message = "College must be at most 150 characters")
        String college,

        @NotBlank(message = "Course is required")
        @Size(max = 100, message = "Course must be at most 100 characters")
        String course,

        @NotBlank(message = "Branch is required")
        @Size(max = 100, message = "Branch must be at most 100 characters")
        String branch,

        @NotNull(message = "Year is required")
        @Min(value = 1, message = "Year must be between 1 and 6")
        @Max(value = 6, message = "Year must be between 1 and 6")
        Integer year,

        @NotNull(message = "Exam path is required")
        Exam examPath,

        @Positive(message = "Rank must be positive")
        Integer rank,

        @Size(max = 2000, message = "Bio must be at most 2000 characters")
        String bio,

        @NotBlank(message = "Location is required")
        @Size(max = 100, message = "Location must be at most 100 characters")
        String location,

        @NotEmpty(message = "At least one expertise topic is required")
        @Size(max = 10, message = "At most 10 expertise topics are allowed")
        List<@NotBlank(message = "Expertise topic must not be blank")
        @Size(max = 100, message = "Expertise topic must be at most 100 characters") String> expertise
) {
}