package com.edumentor.payment.dto;

import jakarta.validation.constraints.NotNull;

public record CreatePaymentRequest(@NotNull(message = "Booking id is required") Long bookingId) {
}