package com.edumentor.payment.controller;

import com.edumentor.payment.dto.WebhookResponse;
import com.edumentor.payment.entity.Payment;
import com.edumentor.payment.exception.ApiException;
import com.edumentor.payment.provider.MockPaymentProvider;
import com.edumentor.payment.repository.PaymentRepository;
import com.edumentor.payment.service.WebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Profile("dev")
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock", matchIfMissing = true)
@RestController
@RequestMapping("/api/payments/dev")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Dev tools")
@SecurityRequirement(name = "bearerAuth")
public class DevPaymentController {

    public enum Outcome {
        SUCCESS,
        FAILED
    }

    private final PaymentRepository paymentRepository;
    private final MockPaymentProvider mockProvider;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    @PostMapping("/{id}/simulate")
    @Operation(summary = "DEV: deliver a signed mock webhook for this payment")
    public WebhookResponse simulate(@PathVariable Long id, @RequestParam(defaultValue = "SUCCESS") Outcome outcome)
            throws JsonProcessingException {
        Payment payment = paymentRepository.findById(id).orElseThrow(ApiException::paymentNotFound);
        if (payment.getProviderPaymentId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_NOT_REGISTERED",
                    "The payment has not been registered with the provider yet");
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "evt_dev_" + UUID.randomUUID());
        event.put("type", outcome == Outcome.SUCCESS ? "payment.succeeded" : "payment.failed");
        event.put("providerPaymentId", payment.getProviderPaymentId());
        if (outcome == Outcome.FAILED) {
            event.put("reason", "Card declined (simulated)");
        }
        String body = objectMapper.writeValueAsString(event);

        HttpHeaders headers = new HttpHeaders();
        headers.set(MockPaymentProvider.SIGNATURE_HEADER, mockProvider.sign(body));
        WebhookService.Result result = webhookService.handle(mockProvider.name(), body, headers);
        return new WebhookResponse(result.name().toLowerCase());
    }
}