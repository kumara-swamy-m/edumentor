package com.edumentor.mentor.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record AvailabilityResponse(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
}