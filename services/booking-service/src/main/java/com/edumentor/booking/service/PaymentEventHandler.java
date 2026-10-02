package com.edumentor.booking.service;

import com.edumentor.booking.dto.BookingResponse;
import com.edumentor.booking.exception.ApiException;
import com.edumentor.booking.meeting.MeetingProvider;
import com.edumentor.booking.meeting.MeetingRequest;
import com.edumentor.booking.web.CorrelationIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventHandler {

    /** Business outcomes that mean "this booking cannot take the money", not infrastructure failures. */
    private static final Set<String> REJECTION_CODES =
            Set.of("BOOKING_NOT_FOUND", "BOOKING_NOT_PENDING", "INVALID_BOOKING_STATE");

    public record PaymentSuccessMessage(Long paymentId, Long bookingId, Long studentId) {
    }

    public record PaymentFailedMessage(Long paymentId, Long bookingId, Long studentId, String reason) {
    }

    private final BookingService bookingService;
    private final MeetingProvider meetingProvider;
    private final ObjectMapper objectMapper;

    public void handle(String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Malformed payment event", ex);
        }
        String correlationId = root.path("correlationId").asText(null);
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        }
        try {
            String type = root.path("eventType").asText("");
            switch (type) {
                case "PAYMENT_SUCCESS" -> onSuccess(read(root, PaymentSuccessMessage.class));
                case "PAYMENT_FAILED" -> onFailed(read(root, PaymentFailedMessage.class));
                default -> log.debug("Payment event ignored: type={}", type);
            }
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private void onSuccess(PaymentSuccessMessage message) {
        log.info("Kafka event consumed: type=PAYMENT_SUCCESS, bookingId={}, paymentId={}",
                message.bookingId(), message.paymentId());
        BookingResponse confirmed;
        try {
            confirmed = bookingService.confirmPayment(message.bookingId(), message.paymentId());
        } catch (ApiException ex) {
            if (REJECTION_CODES.contains(ex.getCode())) {
                bookingService.rejectPayment(message.bookingId(), message.paymentId(), message.studentId(),
                        ex.getMessage());
                return;
            }
            throw ex;
        }
        if (bookingService.isConfirmationAnnounced(message.bookingId())) {
            return;
        }
        String meetingUrl = confirmed.meetingUrl() != null ? confirmed.meetingUrl() : createMeeting(confirmed);
        bookingService.announceConfirmation(message.bookingId(), meetingUrl);
    }

    private void onFailed(PaymentFailedMessage message) {
        log.info("Kafka event consumed: type=PAYMENT_FAILED, bookingId={}, paymentId={}",
                message.bookingId(), message.paymentId());
        try {
            bookingService.cancelAfterPaymentFailure(message.bookingId(), message.reason());
        } catch (ApiException ex) {
            // Already confirmed or unknown: a retry cannot change that
            log.warn("Payment failure not applied: bookingId={}, code={}", message.bookingId(), ex.getCode());
        }
    }

    /** A meeting problem must not undo a paid booking: confirm without a link rather than fail. */
    private String createMeeting(BookingResponse booking) {
        try {
            return meetingProvider.createMeeting(new MeetingRequest(booking.id(),
                    "EduMentor counselling session #" + booking.id(), booking.sessionStart(),
                    booking.sessionEnd()));
        } catch (RuntimeException ex) {
            log.warn("Meeting creation failed, confirming without a link: bookingId={}, cause={}",
                    booking.id(), ex.getClass().getSimpleName());
            return null;
        }
    }

    private <T> T read(JsonNode root, Class<T> type) {
        try {
            return objectMapper.treeToValue(root, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Malformed payment event", ex);
        }
    }
}