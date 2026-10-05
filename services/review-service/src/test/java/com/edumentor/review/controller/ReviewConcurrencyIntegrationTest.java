package com.edumentor.review.controller;

import com.edumentor.review.client.BookingClient;
import com.edumentor.review.client.BookingInfo;
import com.edumentor.review.dto.CreateReviewRequest;
import com.edumentor.review.entity.OutboxEvent;
import com.edumentor.review.entity.Rating;
import com.edumentor.review.repository.OutboxEventRepository;
import com.edumentor.review.repository.RatingRepository;
import com.edumentor.review.repository.ReviewRepository;
import com.edumentor.review.security.AuthenticatedUser;
import com.edumentor.review.service.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ReviewConcurrencyIntegrationTest {

    private static final int REVIEWERS = 6;

    @Autowired
    private ReviewService reviewService;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private RatingRepository ratingRepository;
    @Autowired
    private OutboxEventRepository outboxRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingClient bookingClient;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        reviewRepository.deleteAll();
        ratingRepository.deleteAll();
        when(bookingClient.getBooking(anyLong(), anyString(), any())).thenAnswer(invocation -> {
            long bookingId = invocation.getArgument(0);
            return new BookingInfo(bookingId, 1000 + bookingId, 7L, "COMPLETED");
        });
    }

    @Test
    void concurrentReviewsOfOneMentorNeverLoseAnUpdate() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(REVIEWERS);
        CountDownLatch ready = new CountDownLatch(REVIEWERS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        long expectedSum = 0;
        try {
            for (int i = 0; i < REVIEWERS; i++) {
                long bookingId = 100 + i;
                int stars = i % 5 + 1;
                expectedSum += stars;
                Callable<Long> task = () -> {
                    ready.countDown();
                    go.await();
                    return reviewService.createReview(
                            new AuthenticatedUser(1000 + bookingId, "s@test.com", "STUDENT"), "Bearer x",
                            new CreateReviewRequest(bookingId, stars, "ok")).id();
                };
                futures.add(pool.submit(task));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            for (Future<Long> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        Rating rating = ratingRepository.findByMentorId(7L).orElseThrow();
        assertThat(rating.getRatingCount()).isEqualTo(REVIEWERS);
        assertThat(rating.getRatingSum()).isEqualTo(expectedSum);
        assertThat(reviewRepository.count()).isEqualTo(REVIEWERS);

        List<OutboxEvent> events = outboxRepository.findAll().stream()
                .sorted(Comparator.comparing(OutboxEvent::getId)).toList();
        List<Integer> counts = new ArrayList<>();
        for (OutboxEvent event : events) {
            counts.add(objectMapper.readTree(event.getPayload()).get("reviewCount").asInt());
        }
        assertThat(counts).containsExactly(1, 2, 3, 4, 5, 6);
    }
}