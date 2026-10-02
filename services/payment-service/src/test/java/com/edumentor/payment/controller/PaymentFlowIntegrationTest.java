package com.edumentor.payment.controller;

import com.edumentor.payment.client.BookingClient;
import com.edumentor.payment.client.BookingSummary;
import com.edumentor.payment.entity.OutboxEvent;
import com.edumentor.payment.entity.OutboxEventType;
import com.edumentor.payment.entity.Payment;
import com.edumentor.payment.entity.PaymentStatus;
import com.edumentor.payment.exception.ApiException;
import com.edumentor.payment.provider.HmacSigner;
import com.edumentor.payment.repository.OutboxEventRepository;
import com.edumentor.payment.repository.PaymentRepository;
import com.edumentor.payment.repository.PaymentTransactionRepository;
import com.edumentor.payment.repository.ProcessedWebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentFlowIntegrationTest {

    private static final String SECRET = "test-only-secret-key-that-is-at-least-32-bytes-long";
    private static final String WEBHOOK_SECRET = "test-mock-webhook-secret-123456";
    private static final String AUTH = "Authorization";
    private static final String IDEM = "Idempotency-Key";
    private static final long BOOKING_ID = 50;
    private static final long STUDENT_A = 200;
    private static final long STUDENT_B = 201;
    private static final long MENTOR = 100;
    private static final long ADMIN = 1;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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
        when(bookingClient.getBooking(eq(BOOKING_ID), anyString(), any()))
                .thenReturn(booking(BOOKING_ID, STUDENT_A, "PENDING_PAYMENT", Instant.now().plus(10, ChronoUnit.MINUTES)));
    }

    // ---------- Creating payments ----------

    @Test
    void paymentUsesTheBookingPriceNotAnythingTheClientSends() throws Exception {
        create(STUDENT_A, "pay-key-0001", "{\"bookingId\":50,\"amount\":1}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(500.0))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.providerPaymentId").isNotEmpty())
                .andExpect(jsonPath("$.clientSecret").isNotEmpty());
    }

    @Test
    void repeatedIdempotencyKeyReturnsTheSamePaymentWithoutCreatingAnother() throws Exception {
        long first = createAndGetId(STUDENT_A, "pay-key-0001");

        String replay = create(STUDENT_A, "pay-key-0001", "{\"bookingId\":50}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(replay).get("id").asLong()).isEqualTo(first);
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void replayStillWorksAfterTheBookingIsNoLongerPayable() throws Exception {
        long first = createAndGetId(STUDENT_A, "pay-key-0001");
        when(bookingClient.getBooking(eq(BOOKING_ID), anyString(), any()))
                .thenReturn(booking(BOOKING_ID, STUDENT_A, "CONFIRMED", null));

        String replay = create(STUDENT_A, "pay-key-0001", "{\"bookingId\":50}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(replay).get("id").asLong()).isEqualTo(first);
    }

    @Test
    void sameKeyForADifferentBookingIsRejectedWith422() throws Exception {
        createAndGetId(STUDENT_A, "pay-key-0001");

        create(STUDENT_A, "pay-key-0001", "{\"bookingId\":51}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void secondPaymentForTheSameBookingIsRejectedWith409() throws Exception {
        createAndGetId(STUDENT_A, "pay-key-0001");

        create(STUDENT_A, "pay-key-0002", "{\"bookingId\":50}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PAYMENT_ALREADY_EXISTS"));
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void idempotencyKeyIsRequiredAndValidated() throws Exception {
        mockMvc.perform(post("/api/payments").header(AUTH, bearer(STUDENT_A, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_HEADER"));

        create(STUDENT_A, "bad key!", "{\"bookingId\":50}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_IDEMPOTENCY_KEY"));
        create(STUDENT_A, "short", "{\"bookingId\":50}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_IDEMPOTENCY_KEY"));
        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    void bookingThatIsNotPayableIsRejected() throws Exception {
        when(bookingClient.getBooking(eq(BOOKING_ID), anyString(), any()))
                .thenReturn(booking(BOOKING_ID, STUDENT_A, "CONFIRMED", null));
        create(STUDENT_A, "pay-key-0001", "{\"bookingId\":50}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_PAYABLE"));

        when(bookingClient.getBooking(eq(BOOKING_ID), anyString(), any()))
                .thenReturn(booking(BOOKING_ID, STUDENT_A, "PENDING_PAYMENT", Instant.now().minusSeconds(5)));
        create(STUDENT_A, "pay-key-0002", "{\"bookingId\":50}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_PAYABLE"));
        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    void someoneElsesBookingLooksLikeItDoesNotExist() throws Exception {
        create(STUDENT_B, "pay-key-0001", "{\"bookingId\":50}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    void paymentFailsClosedWhenBookingServiceIsDown() throws Exception {
        when(bookingClient.getBooking(eq(BOOKING_ID), anyString(), any()))
                .thenThrow(ApiException.bookingServiceUnavailable());

        create(STUDENT_A, "pay-key-0001", "{\"bookingId\":50}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("BOOKING_SERVICE_UNAVAILABLE"));
        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    void onlyStudentsCanPayAndAnonymousIsRejected() throws Exception {
        create(MENTOR, "pay-key-0001", "{\"bookingId\":50}", "MENTOR").andExpect(status().isForbidden());
        mockMvc.perform(post("/api/payments").header(IDEM, "pay-key-0001")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":50}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Webhooks ----------

    @Test
    void signedSuccessWebhookConfirmsThePaymentAndRecordsAnOutboxEvent() throws Exception {
        long id = createAndGetId(STUDENT_A, "pay-key-0001");

        webhook(succeededBody("evt_1", providerPaymentId(id)), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processed"));

        Payment payment = paymentRepository.findById(id).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getCompletedAt()).isNotNull();

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo(OutboxEventType.PAYMENT_SUCCESS);
        assertThat(events.get(0).getPayload()).contains("\"bookingId\":50").contains("\"paymentId\":" + id);
        assertThat(transactionRepository.findByPaymentIdOrderByIdAsc(id))
                .extracting(t -> t.getType().name())
                .containsExactly("PAYMENT_CREATED", "PROVIDER_REGISTERED", "PAYMENT_SUCCEEDED");
    }

    @Test
    void webhookWithInvalidSignatureChangesNothing() throws Exception {
        long id = createAndGetId(STUDENT_A, "pay-key-0001");
        String body = succeededBody("evt_1", providerPaymentId(id));

        mockMvc.perform(post("/api/payments/webhooks/mock").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Mock-Signature", HmacSigner.hmacSha256Hex("not-the-real-secret-123456", body))
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_WEBHOOK_SIGNATURE"));
        mockMvc.perform(post("/api/payments/webhooks/mock").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/payments/webhooks/mock").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Mock-Signature", HmacSigner.hmacSha256Hex(WEBHOOK_SECRET, body))
                        .content(body.replace("evt_1", "evt_tampered")))
                .andExpect(status().isBadRequest());

        assertThat(paymentRepository.findById(id).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(outboxEventRepository.count()).isZero();
        assertThat(processedEventRepository.count()).isZero();
    }

    @Test
    void duplicateWebhookDeliveryIsProcessedOnlyOnce() throws Exception {
        long id = createAndGetId(STUDENT_A, "pay-key-0001");
        String body = succeededBody("evt_1", providerPaymentId(id));

        webhook(body, true).andExpect(jsonPath("$.status").value("processed"));
        webhook(body, true).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("duplicate"));

        assertThat(outboxEventRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    @Test
    void anotherSuccessEventForAnAlreadyPaidPaymentPublishesNothingNew() throws Exception {
        long id = createAndGetId(STUDENT_A, "pay-key-0001");

        webhook(succeededBody("evt_1", providerPaymentId(id)), true).andExpect(jsonPath("$.status").value("processed"));
        webhook(succeededBody("evt_2", providerPaymentId(id)), true).andExpect(status().isOk());

        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void failureWebhookMarksFailedAndLaterSuccessIsStillHonoured() throws Exception {
        long id = createAndGetId(STUDENT_A, "pay-key-0001");
        String pid = providerPaymentId(id);

        webhook(failedBody("evt_1", pid), true).andExpect(status().isOk());
        assertThat(paymentRepository.findById(id).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(outboxEventRepository.findAll()).hasSize(1)
                .allSatisfy(e -> {
                    assertThat(e.getEventType()).isEqualTo(OutboxEventType.PAYMENT_FAILED);
                    assertThat(e.getPayload()).contains("Card declined");
                });

        // The customer retried and the money moved: it must not be lost
        webhook(succeededBody("evt_2", pid), true).andExpect(status().isOk());
        assertThat(paymentRepository.findById(id).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(outboxEventRepository.count()).isEqualTo(2);
    }

    @Test
    void failureAfterSuccessIsIgnored() throws Exception {
        long id = createAndGetId(STUDENT_A, "pay-key-0001");
        String pid = providerPaymentId(id);

        webhook(succeededBody("evt_1", pid), true).andExpect(status().isOk());
        webhook(failedBody("evt_2", pid), true).andExpect(status().isOk());

        assertThat(paymentRepository.findById(id).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void webhookForUnknownPaymentIsAcknowledgedAndIgnored() throws Exception {
        webhook(succeededBody("evt_1", "mock_pi_99999"), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"));
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void webhookForAnInactiveProviderIsNotFound() throws Exception {
        String body = succeededBody("evt_1", "pi_1");

        mockMvc.perform(post("/api/payments/webhooks/stripe").contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=00").content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("WEBHOOK_PROVIDER_UNKNOWN"));
    }

    // ---------- Verify, visibility, listing ----------

    @Test
    void verifyNeverMarksPaidOnItsOwnWhenTheProviderIsStillPending() throws Exception {
        long id = createAndGetId(STUDENT_A, "pay-key-0001");

        mockMvc.perform(post("/api/payments/" + id + "/verify").header(AUTH, bearer(STUDENT_A, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(post("/api/payments/" + id + "/verify").header(AUTH, bearer(STUDENT_B, "STUDENT")))
                .andExpect(status().isNotFound());
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void paymentsAreVisibleOnlyToTheOwnerAndAdmins() throws Exception {
        long id = createAndGetId(STUDENT_A, "pay-key-0001");

        mockMvc.perform(get("/api/payments/" + id).header(AUTH, bearer(STUDENT_A, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").isNotEmpty());
        mockMvc.perform(get("/api/payments/" + id).header(AUTH, bearer(STUDENT_B, "STUDENT")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/payments/" + id).header(AUTH, bearer(ADMIN, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").doesNotExist());

        mockMvc.perform(get("/api/payments/me").header(AUTH, bearer(STUDENT_A, "STUDENT")))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/payments/me").header(AUTH, bearer(STUDENT_B, "STUDENT")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void clientSecretIsHiddenOnceThePaymentIsNoLongerPending() throws Exception {
        long id = createAndGetId(STUDENT_A, "pay-key-0001");
        webhook(succeededBody("evt_1", providerPaymentId(id)), true).andExpect(status().isOk());

        mockMvc.perform(get("/api/payments/" + id).header(AUTH, bearer(STUDENT_A, "STUDENT")))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }

    @Test
    void adminListingRequiresAdmin() throws Exception {
        createAndGetId(STUDENT_A, "pay-key-0001");

        mockMvc.perform(get("/api/payments/admin/all").header(AUTH, bearer(STUDENT_A, "STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/payments/admin/all?status=PENDING").header(AUTH, bearer(ADMIN, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].clientSecret").doesNotExist());
        mockMvc.perform(get("/api/payments/admin/all?status=SUCCESS").header(AUTH, bearer(ADMIN, "ADMIN")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---------- helpers ----------

    private ResultActions create(long userId, String key, String body) throws Exception {
        return create(userId, key, body, "STUDENT");
    }

    private ResultActions create(long userId, String key, String body, String role) throws Exception {
        return mockMvc.perform(post("/api/payments").header(AUTH, bearer(userId, role)).header(IDEM, key)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createAndGetId(long userId, String key) throws Exception {
        String response = create(userId, key, "{\"bookingId\":50}").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private String providerPaymentId(long paymentId) {
        return paymentRepository.findById(paymentId).orElseThrow().getProviderPaymentId();
    }

    /** Webhooks carry no JWT: they are authenticated only by the signature. */
    private ResultActions webhook(String body, boolean sign) throws Exception {
        var request = post("/api/payments/webhooks/mock").contentType(MediaType.APPLICATION_JSON).content(body);
        if (sign) {
            request.header("X-Mock-Signature", HmacSigner.hmacSha256Hex(WEBHOOK_SECRET, body));
        }
        return mockMvc.perform(request);
    }

    private String succeededBody(String eventId, String providerPaymentId) throws Exception {
        return eventBody(eventId, "payment.succeeded", providerPaymentId, null);
    }

    private String failedBody(String eventId, String providerPaymentId) throws Exception {
        return eventBody(eventId, "payment.failed", providerPaymentId, "Card declined");
    }

    private String eventBody(String eventId, String type, String providerPaymentId, String reason) throws Exception {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", eventId);
        event.put("type", type);
        event.put("providerPaymentId", providerPaymentId);
        if (reason != null) {
            event.put("reason", reason);
        }
        return objectMapper.writeValueAsString(event);
    }

    private BookingSummary booking(long id, long studentId, String status, Instant holdExpiresAt) {
        return new BookingSummary(id, studentId, status, new BigDecimal("500.00"), "INR", holdExpiresAt);
    }

    private String bearer(long userId, String role) {
        Date now = new Date();
        String token = Jwts.builder()
                .subject("user" + userId + "@test.com")
                .claim("userId", userId)
                .claim("email", "user" + userId + "@test.com")
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return "Bearer " + token;
    }
}