package com.edumentor.notification;

import com.edumentor.notification.client.AuthClient;
import com.edumentor.notification.client.UserInfo;
import com.edumentor.notification.email.EmailMessage;
import com.edumentor.notification.email.EmailProvider;
import com.edumentor.notification.entity.NotificationStatus;
import com.edumentor.notification.repository.NotificationRepository;
import com.edumentor.notification.service.NotificationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationFlowIntegrationTest {

    private static final String SECRET = "test-only-secret-key-that-is-at-least-32-bytes-long";
    private static final String CONFIRMED = "{\"eventId\":\"evt-1\",\"eventType\":\"BOOKING_CONFIRMED\","
            + "\"correlationId\":\"corr-1\",\"bookingId\":50,\"studentId\":200,\"mentorUserId\":100,"
            + "\"sessionStart\":\"2026-10-10T10:00:00Z\",\"meetingUrl\":\"https://meet.edumentor.local/session-50\"}";

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationRepository repository;
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthClient authClient;
    @MockBean
    private EmailProvider emailProvider;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        when(authClient.getUser(eq(200L), anyString())).thenReturn(new UserInfo(200L, "Asha", "asha@example.com"));
        when(authClient.getUser(eq(100L), anyString())).thenReturn(new UserInfo(100L, "Rahul", "rahul@example.com"));
    }

    @Test
    void bookingConfirmationNotifiesStudentAndMentorWithTheMeetingLink() {
        notificationService.handleEvent(CONFIRMED);

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailProvider, times(2)).send(captor.capture());
        List<EmailMessage> sent = captor.getAllValues();
        assertThat(sent).extracting(EmailMessage::to).containsExactlyInAnyOrder("asha@example.com",
                "rahul@example.com");
        assertThat(sent).allSatisfy(m -> assertThat(m.body()).contains("https://meet.edumentor.local/session-50"));
        assertThat(sent.get(0).body()).startsWith("Hi ");
        assertThat(repository.findAll()).hasSize(2)
                .allSatisfy(n -> assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT));
    }

    @Test
    void redeliveredEventsAreNotSentTwice() {
        notificationService.handleEvent(CONFIRMED);
        notificationService.handleEvent(CONFIRMED);

        verify(emailProvider, times(2)).send(any());
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void paymentReceiptGoesOnlyToTheStudent() {
        notificationService.handleEvent("{\"eventId\":\"evt-2\",\"eventType\":\"PAYMENT_SUCCESS\","
                + "\"bookingId\":50,\"studentId\":200,\"amount\":500.00,\"currency\":\"INR\"}");

        assertThat(repository.findAll()).singleElement().satisfies(n -> {
            assertThat(n.getUserId()).isEqualTo(200L);
            assertThat(n.getSubject()).isEqualTo("Payment received");
            assertThat(n.getBody()).contains("500.0").contains("INR");
        });
    }

    @Test
    void refundExpiryAndFailureEventsProduceTheirOwnMessages() {
        notificationService.handleEvent("{\"eventId\":\"e3\",\"eventType\":\"PAYMENT_REFUNDED\",\"bookingId\":50,"
                + "\"studentId\":200,\"amount\":500,\"currency\":\"INR\",\"reason\":\"Booking expired\"}");
        notificationService.handleEvent("{\"eventId\":\"e4\",\"eventType\":\"BOOKING_CANCELLED\",\"bookingId\":51,"
                + "\"studentId\":200,\"status\":\"EXPIRED\",\"reason\":\"Payment window expired\"}");
        notificationService.handleEvent("{\"eventId\":\"e5\",\"eventType\":\"PAYMENT_FAILED\",\"bookingId\":52,"
                + "\"studentId\":200,\"reason\":\"Card declined\"}");

        assertThat(repository.findAll()).extracting(n -> n.getSubject()).containsExactlyInAnyOrder(
                "Your payment has been refunded", "Your slot hold expired", "Your payment did not go through");
    }

    @Test
    void sessionReminderNotifiesBothParticipants() {
        notificationService.handleEvent("{\"eventId\":\"e6\",\"eventType\":\"SESSION_REMINDER\",\"bookingId\":50,"
                + "\"studentId\":200,\"mentorUserId\":100,\"sessionStart\":\"2026-10-10T10:00:00Z\"}");

        assertThat(repository.findAll()).hasSize(2)
                .allSatisfy(n -> assertThat(n.getSubject()).isEqualTo("Your session starts soon"));
    }

    @Test
    void emailFailureIsRecordedAndDoesNotFailTheEvent() {
        doThrow(new IllegalStateException("smtp down")).when(emailProvider).send(any());

        notificationService.handleEvent("{\"eventId\":\"e7\",\"eventType\":\"PAYMENT_FAILED\",\"bookingId\":52,"
                + "\"studentId\":200,\"reason\":\"Card declined\"}");

        assertThat(repository.findAll()).singleElement().satisfies(n -> {
            assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(n.getErrorMessage()).contains("smtp down");
        });
    }

    @Test
    void userLookupFailureIsRethrownSoKafkaCanRetry() {
        when(authClient.getUser(eq(200L), anyString())).thenThrow(new IllegalStateException("auth down"));

        assertThatThrownBy(() -> notificationService.handleEvent(CONFIRMED))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.count()).isZero();
        verify(emailProvider, never()).send(any());
    }

    @Test
    void eventsThatNeedNoNotificationAndBadEventsAreHandled() {
        notificationService.handleEvent("{\"eventId\":\"e8\",\"eventType\":\"BOOKING_CREATED\",\"studentId\":200}");
        assertThat(repository.count()).isZero();

        assertThatThrownBy(() -> notificationService.handleEvent("not json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> notificationService.handleEvent(
                "{\"eventType\":\"PAYMENT_FAILED\",\"studentId\":200}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usersSeeOnlyTheirOwnNotifications() throws Exception {
        notificationService.handleEvent(CONFIRMED);

        mockMvc.perform(get("/api/notifications/me").header("Authorization", bearer(200, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("BOOKING_CONFIRMATION"))
                .andExpect(jsonPath("$.content[0].recipientEmail").doesNotExist());
        mockMvc.perform(get("/api/notifications/me").header("Authorization", bearer(999, "STUDENT")))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/notifications/me")).andExpect(status().isUnauthorized());
    }

    private String bearer(long userId, String role) {
        Date now = new Date();
        return "Bearer " + Jwts.builder()
                .subject("user" + userId + "@test.com")
                .claim("userId", userId)
                .claim("email", "user" + userId + "@test.com")
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}