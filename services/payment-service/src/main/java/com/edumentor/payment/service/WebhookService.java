package com.edumentor.payment.service;

import com.edumentor.payment.entity.Payment;
import com.edumentor.payment.entity.ProcessedWebhookEvent;
import com.edumentor.payment.exception.ApiException;
import com.edumentor.payment.provider.InvalidWebhookException;
import com.edumentor.payment.provider.PaymentProvider;
import com.edumentor.payment.provider.ProviderEvent;
import com.edumentor.payment.provider.ProviderOutcome;
import com.edumentor.payment.repository.PaymentRepository;
import com.edumentor.payment.repository.ProcessedWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    public enum Result {
        PROCESSED,
        DUPLICATE,
        IGNORED
    }

    /** Thrown inside the transaction when the event id was already recorded (including by a concurrent delivery). */
    private static final class DuplicateWebhookException extends RuntimeException {
        DuplicateWebhookException() {
            super("Duplicate webhook event", null, false, false);
        }
    }

    private final PaymentProvider provider;
    private final PaymentRepository paymentRepository;
    private final ProcessedWebhookEventRepository processedEventRepository;
    private final PaymentProcessor paymentProcessor;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public Result handle(String providerName, String rawBody, HttpHeaders headers) {
        if (!provider.name().equalsIgnoreCase(providerName)) {
            throw ApiException.webhookProviderUnknown();
        }

        ProviderEvent event;
        try {
            event = provider.parseWebhook(rawBody, headers);
        } catch (InvalidWebhookException ex) {
            // The reason is logged; the body and headers never are
            log.warn("Rejected webhook for provider={}: {}", provider.name(), ex.getMessage());
            throw ApiException.invalidWebhookSignature();
        }

        if (event.outcome() == ProviderOutcome.IGNORED) {
            log.debug("Webhook event ignored: eventId={}", event.eventId());
            return Result.IGNORED;
        }
        if (processedEventRepository.existsByProviderAndProviderEventId(provider.name(), event.eventId())) {
            log.info("Duplicate webhook skipped: eventId={}", event.eventId());
            return Result.DUPLICATE;
        }

        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> process(event)));
        } catch (DuplicateWebhookException ex) {
            log.info("Duplicate webhook skipped (concurrent delivery): eventId={}", event.eventId());
            return Result.DUPLICATE;
        }
    }

    private Result process(ProviderEvent event) {
        try {
            // Unique (provider, event id): a concurrent delivery of the same event fails here
            processedEventRepository.saveAndFlush(new ProcessedWebhookEvent(provider.name(), event.eventId(),
                    event.outcome().name(), Instant.now(clock)));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateWebhookException();
        }

        Optional<Payment> payment = paymentRepository.findByProviderAndProviderPaymentIdForUpdate(
                provider.name(), event.providerPaymentId());
        if (payment.isEmpty()) {
            log.warn("Webhook for unknown provider payment ignored: eventId={}", event.eventId());
            return Result.IGNORED;
        }
        paymentProcessor.apply(payment.get(), event.outcome(), "WEBHOOK", event.eventId(), event.reason());
        return Result.PROCESSED;
    }
}