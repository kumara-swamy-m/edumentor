package com.edumentor.mentor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateAvailabilityRequest(
        @NotNull(message = "Windows are required")
        @Size(max = 50, message = "At most 50 windows are allowed")
        List<@Valid AvailabilityRequest> windows
) {
}