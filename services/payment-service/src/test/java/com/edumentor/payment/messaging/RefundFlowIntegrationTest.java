package com.edumentor.payment.messaging;

import com.edumentor.payment.client.BookingClient;
import com.edumentor.payment.client.BookingSummary;
import com.edumentor.payment.dto.CreatePaymentResult;
import com.edumentor.payment.entity.OutboxEvent;
import com.edumentor.payment.entity.OutboxEventType;
import com.edumentor.payment.entity.PaymentStatus;
import com.edumentor.payment.provider.HmacSigner;
import com.edumentor.payment.repository.OutboxEventRepository;
import com.edumentor.payment.repository.PaymentRepository;
import com.edumentor.payment.repository.PaymentTransactionRepository;
import com.edumentor.payment.repository.ProcessedWebhookEventRepository;
import com.edumentor.payment.security.AuthenticatedUser;
import com.edumentor.payment.service.PaymentService;
import com.edumentor.payment.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class RefundFlowIntegrationTest {

    private static final String WEBHOOK_SECRET = "test-mock-webhook-secret-123456";

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private WebhookService webhookService;
    @Autowired
    private BookingEventHandler handler;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PaymentTransactionRepository transactionRepository;
    @Autowired
    private ProcessedWebhookEventRepository processedEventRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private BookingClient bookingClient;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();
        transactionRepository.deleteAll();
        paymentRepository.deleteAll();
        when(bookingClient.getBooking(eq(50L), anyString(), any())).thenReturn(new BookingSummary(50L, 200L,
                "PENDING_PAYMENT", new BigDecimal("500.00"), "INR", Instant.now().plus(10, ChronoUnit.MINUTES)));
    }

    @Test
    void rejectedPaymentIsRefundedOnceAndAnnounced() {
        long paymentId = paidPayment();

        handler.handle(rejected(paymentId));
        handler.handle(rejected(paymentId));

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).extracting(OutboxEvent::getEventType)
                .containsExactlyInAnyOrder(OutboxEventType.PAYMENT_SUCCESS, OutboxEventType.PAYMENT_REFUNDED);
        assertThat(events).filteredOn(e -> e.getEventType() == OutboxEventType.PAYMENT_REFUNDED)
                .singleElement()
                .satisfies(e -> assertThat(e.getPayload()).contains("\"bookingId\":50"));
    }

    @Test
    void paymentThatIsNotSuccessfulIsNeverRefunded() throws Exception {
        CreatePaymentResult created = paymentService.createPayment(
                new AuthenticatedUser(200L, "s@test.com", "STUDENT"), "Bearer test", "pay-key-0001", 50L);

        handler.handle(rejected(created.payment().id()));

        assertThat(paymentRepository.findById(created.payment().id()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void unknownPaymentAndOtherEventTypesAreIgnored() {
        handler.handle(rejected(99999L));
        handler.handle("{\"eventType\":\"BOOKING_CONFIRMED\",\"bookingId\":50}");

        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void malformedEventsAreRejectedForTheDeadLetterTopic() {
        assertThatThrownBy(() -> handler.handle("not json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle("{\"eventType\":\"BOOKING_PAYMENT_REJECTED\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private long paymentId;

    private long paidPayment() {
        CreatePaymentResult created = paymentService.createPayment(
                new AuthenticatedUser(200L, "s@test.com", "STUDENT"), "Bearer test", "pay-key-0001", 50L);
        String body = "{\"id\":\"evt_1\",\"type\":\"payment.succeeded\",\"providerPaymentId\":\""
                + created.payment().providerPaymentId() + "\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Mock-Signature", HmacSigner.hmacSha256Hex(WEBHOOK_SECRET, body));
        webhookService.handle("mock", body, headers);
        paymentId = created.payment().id();
        return paymentId;
    }

    private String rejected(long paymentId) {
        return "{\"eventId\":\"e1\",\"eventType\":\"BOOKING_PAYMENT_REJECTED\",\"correlationId\":\"corr-1\","
                + "\"bookingId\":50,\"paymentId\":" + paymentId + ",\"studentId\":200,\"reason\":\"Booking expired\"}";
    }
}