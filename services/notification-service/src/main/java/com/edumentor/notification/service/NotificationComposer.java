package com.edumentor.notification.service;

import com.edumentor.notification.entity.NotificationType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class NotificationComposer {

    public record Draft(Long userId, NotificationType type, String subject, String body) {
    }

    private static final DateTimeFormatter WHEN = DateTimeFormatter
            .ofPattern("EEE, d MMM yyyy 'at' h:mm a z", Locale.ENGLISH)
            .withZone(ZoneId.of("Asia/Kolkata"));

    /** Returns no drafts for event types that need no notification. */
    public List<Draft> compose(String eventType, JsonNode e) {
        return switch (eventType) {
            case "BOOKING_CONFIRMED" -> bookingConfirmed(e);
            case "PAYMENT_SUCCESS" -> single(e, "studentId", NotificationType.PAYMENT_RECEIPT,
                    "Payment received",
                    "We received your payment of " + amount(e) + " for booking #" + e.path("bookingId").asText()
                            + ". Your session will be confirmed shortly.");
            case "PAYMENT_FAILED" -> single(e, "studentId", NotificationType.PAYMENT_FAILED,
                    "Your payment did not go through",
                    "Your payment for booking #" + e.path("bookingId").asText() + " failed: "
                            + e.path("reason").asText("the payment was declined") + ". The slot has been released.");
            case "PAYMENT_REFUNDED" -> single(e, "studentId", NotificationType.PAYMENT_REFUND,
                    "Your payment has been refunded",
                    "We refunded " + amount(e) + " for booking #" + e.path("bookingId").asText() + ". Reason: "
                            + e.path("reason").asText("the booking could not be honoured") + ".");
            case "BOOKING_CANCELLED" -> bookingCancelled(e);
            case "SESSION_REMINDER" -> reminder(e);
            default -> List.of();
        };
    }

    private List<Draft> bookingConfirmed(JsonNode e) {
        String link = e.path("meetingUrl").asText(null);
        String linkLine = link == null ? "A meeting link will be shared with you shortly." : "Join here: " + link;
        String when = when(e, "sessionStart");

        List<Draft> drafts = new ArrayList<>();
        addIfPresent(drafts, e, "studentId", NotificationType.BOOKING_CONFIRMATION,
                "Your EduMentor session is confirmed",
                "Your counselling session is confirmed for " + when + ".\n" + linkLine);
        addIfPresent(drafts, e, "mentorUserId", NotificationType.BOOKING_CONFIRMATION,
                "New EduMentor session booked",
                "A student booked a counselling session with you for " + when + ".\n" + linkLine);
        return drafts;
    }

    private List<Draft> bookingCancelled(JsonNode e) {
        boolean expired = "EXPIRED".equals(e.path("status").asText());
        return single(e, "studentId", NotificationType.BOOKING_CANCELLATION,
                expired ? "Your slot hold expired" : "Your booking was cancelled",
                (expired ? "The payment window for booking #" : "Booking #") + e.path("bookingId").asText()
                        + (expired ? " ran out and the slot was released." : " was cancelled.")
                        + " Reason: " + e.path("reason").asText("not given") + ".");
    }

    private List<Draft> reminder(JsonNode e) {
        String link = e.path("meetingUrl").asText(null);
        String body = "Your counselling session starts " + when(e, "sessionStart") + "."
                + (link == null ? "" : "\nJoin here: " + link);
        List<Draft> drafts = new ArrayList<>();
        addIfPresent(drafts, e, "studentId", NotificationType.SESSION_REMINDER, "Your session starts soon", body);
        addIfPresent(drafts, e, "mentorUserId", NotificationType.SESSION_REMINDER,
                "Your session starts soon", body);
        return drafts;
    }

    private List<Draft> single(JsonNode e, String userField, NotificationType type, String subject, String text) {
        List<Draft> drafts = new ArrayList<>();
        addIfPresent(drafts, e, userField, type, subject, text);
        return drafts;
    }

    private void addIfPresent(List<Draft> drafts, JsonNode e, String userField, NotificationType type,
                              String subject, String text) {
        long userId = e.path(userField).asLong(0);
        if (userId > 0) {
            drafts.add(new Draft(userId, type, subject, "Hi {name},\n\n" + text + "\n\n- The EduMentor team"));
        }
    }

    private String amount(JsonNode e) {
        return e.path("amount").asText("") + " " + e.path("currency").asText("");
    }

    private String when(JsonNode e, String field) {
        String text = e.path(field).asText(null);
        if (text == null) {
            return "the scheduled time";
        }
        try {
            return WHEN.format(Instant.parse(text));
        } catch (DateTimeException ex) {
            return text;
        }
    }
}