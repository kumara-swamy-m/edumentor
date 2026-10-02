package com.edumentor.payment.controller;

import com.edumentor.payment.client.BookingClient;
import com.edumentor.payment.client.BookingSummary;
import com.edumentor.payment.dto.CreatePaymentResult;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class WebhookConcurrencyIntegrationTest {

    private static final int DELIVERIES = 6;
    private static final String WEBHOOK_SECRET = "test-mock-webhook-secret-123456";

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private WebhookService webhookService;
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
    void sameWebhookEventDeliveredConcurrentlyIsProcessedExactlyOnce() throws Exception {
        CreatePaymentResult created = paymentService.createPayment(
                new AuthenticatedUser(200L, "s@test.com", "STUDENT"), "Bearer test", "pay-key-0001", 50L);
        String body = "{\"id\":\"evt_race\",\"type\":\"payment.succeeded\",\"providerPaymentId\":\""
                + created.payment().providerPaymentId() + "\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Mock-Signature", HmacSigner.hmacSha256Hex(WEBHOOK_SECRET, body));

        ExecutorService pool = Executors.newFixedThreadPool(DELIVERIES);
        CountDownLatch ready = new CountDownLatch(DELIVERIES);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<WebhookService.Result>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < DELIVERIES; i++) {
                Callable<WebhookService.Result> task = () -> {
                    ready.countDown();
                    go.await();
                    return webhookService.handle("mock", body, headers);
                };
                futures.add(pool.submit(task));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();

            List<WebhookService.Result> results = new ArrayList<>();
            for (Future<WebhookService.Result> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn(r -> r == WebhookService.Result.PROCESSED).hasSize(1);
            assertThat(results).filteredOn(r -> r == WebhookService.Result.DUPLICATE).hasSize(DELIVERIES - 1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(outboxEventRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(paymentRepository.findById(created.payment().id()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCESS);
    }
}