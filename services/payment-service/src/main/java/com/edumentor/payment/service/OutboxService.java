package com.edumentor.payment.service;

import com.edumentor.payment.entity.OutboxEvent;
import com.edumentor.payment.entity.OutboxEventType;
import com.edumentor.payment.entity.Payment;
import com.edumentor.payment.event.PaymentFailedEvent;
import com.edumentor.payment.event.PaymentSuccessEvent;
import com.edumentor.payment.repository.OutboxEventRepository;
import com.edumentor.payment.web.CorrelationIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Records domain events in the same transaction as the state change they describe. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(OutboxEventType type, Payment payment, String reason) {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now(clock);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);

        Object payload = switch (type) {
            case PAYMENT_SUCCESS -> new PaymentSuccessEvent(eventId, type.name(), now, correlationId,
                    payment.getId(), payment.getBookingId(), payment.getStudentId(),
                    payment.getAmount(), payment.getCurrency());
            case PAYMENT_FAILED -> new PaymentFailedEvent(eventId, type.name(), now, correlationId,
                    payment.getId(), payment.getBookingId(), payment.getStudentId(),
                    payment.getAmount(), payment.getCurrency(), reason);
        };

        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setAggregateType("PAYMENT");
        event.setAggregateId(String.valueOf(payment.getId()));
        event.setEventType(type);
        event.setCorrelationId(correlationId);
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize outbox event", ex);
        }
        outboxEventRepository.save(event);
        log.info("Outbox event recorded: type={}, paymentId={}, eventId={}", type, payment.getId(), eventId);
    }
}