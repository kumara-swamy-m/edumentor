package com.edumentor.booking.controller;

import com.edumentor.booking.dto.BookingResponse;
import com.edumentor.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Simulates payment outcomes until payment-service and Kafka exist. Active only in the "dev" profile. */
@Profile("dev")
@RestController
@RequestMapping("/api/bookings/dev")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Dev tools (remove in Phase 6)")
@SecurityRequirement(name = "bearerAuth")
public class DevBookingController {

    private final BookingService bookingService;

    @PostMapping("/{id}/confirm")
    @Operation(summary = "DEV: simulate a successful payment")
    public BookingResponse confirm(@PathVariable Long id, @RequestParam(defaultValue = "9001") Long paymentId) {
        return bookingService.confirmPayment(id, paymentId);
    }

    @PostMapping("/{id}/fail")
    @Operation(summary = "DEV: simulate a failed payment")
    public BookingResponse fail(@PathVariable Long id) {
        return bookingService.cancelAfterPaymentFailure(id, "Payment failed (simulated)");
    }
}