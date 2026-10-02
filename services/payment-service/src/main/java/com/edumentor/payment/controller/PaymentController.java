package com.edumentor.payment.controller;

import com.edumentor.payment.dto.CreatePaymentRequest;
import com.edumentor.payment.dto.CreatePaymentResult;
import com.edumentor.payment.dto.PageResponse;
import com.edumentor.payment.dto.PaymentResponse;
import com.edumentor.payment.entity.PaymentStatus;
import com.edumentor.payment.security.AuthenticatedUser;
import com.edumentor.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Start paying for a booking (idempotent). The amount is taken from the booking.")
    public ResponseEntity<PaymentResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Parameter(description = "8-64 chars [A-Za-z0-9_-]; repeat it to safely retry")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        CreatePaymentResult result = paymentService.createPayment(user, authorization, idempotencyKey,
                request.bookingId());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result.payment());
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "My payments, newest first")
    public PageResponse<PaymentResponse> mine(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return paymentService.listMine(user.userId(), page, size);
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "All payments, optionally filtered by status (ADMIN only)")
    public PageResponse<PaymentResponse> all(@RequestParam(required = false) PaymentStatus status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return paymentService.listAll(status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    @Operation(summary = "Get a payment (owner or admin)")
    public PaymentResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return paymentService.getPayment(user, id);
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Ask the server to re-check the payment with the provider")
    public PaymentResponse verify(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return paymentService.verify(user, id);
    }
}