package com.edumentor.payment.messaging;

import com.edumentor.payment.service.RefundService;
import com.edumentor.payment.web.CorrelationIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingEventHandler {

    private final RefundService refundService;
    private final ObjectMapper objectMapper;

    public void handle(String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Malformed booking event", ex);
        }
        String correlationId = root.path("correlationId").asText(null);
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        }
        try {
            String type = root.path("eventType").asText("");
            if ("BOOKING_PAYMENT_REJECTED".equals(type)) {
                long paymentId = root.path("paymentId").asLong(0);
                if (paymentId <= 0) {
                    throw new IllegalArgumentException("BOOKING_PAYMENT_REJECTED without paymentId");
                }
                log.info("Kafka event consumed: type={}, paymentId={}", type, paymentId);
                refundService.refundRejectedPayment(paymentId,
                        root.path("reason").asText("Booking could not be honoured"));
            } else {
                log.debug("Booking event ignored: type={}", type);
            }
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}