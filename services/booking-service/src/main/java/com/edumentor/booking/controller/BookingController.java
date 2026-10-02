package com.edumentor.booking.controller;

import com.edumentor.booking.dto.BookingResponse;
import com.edumentor.booking.dto.CreateBookingRequest;
import com.edumentor.booking.dto.PageResponse;
import com.edumentor.booking.entity.BookingStatus;
import com.edumentor.booking.security.AuthenticatedUser;
import com.edumentor.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Hold a slot (creates a PENDING_PAYMENT booking)")
    public BookingResponse hold(@AuthenticationPrincipal AuthenticatedUser user,
                                @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                @Valid @RequestBody CreateBookingRequest request) {
        return bookingService.createBooking(user.userId(), request.slotId(), authorization);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('STUDENT','MENTOR')")
    @Operation(summary = "My bookings (as student or as mentor), newest first")
    public PageResponse<BookingResponse> myBookings(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @RequestParam(required = false) BookingStatus status,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return bookingService.listMine(user, status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a booking (student, mentor of the booking, or admin)")
    public BookingResponse getBooking(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return bookingService.getBooking(user, id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Cancel my unpaid booking and release the slot")
    public BookingResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return bookingService.cancelByStudent(user.userId(), id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Mark a CONFIRMED booking as COMPLETED after the session has started")
    public BookingResponse complete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return bookingService.completeByMentor(user.userId(), id);
    }
}