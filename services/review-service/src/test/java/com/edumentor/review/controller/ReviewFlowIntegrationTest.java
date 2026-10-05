package com.edumentor.review.controller;

import com.edumentor.review.client.BookingClient;
import com.edumentor.review.client.BookingInfo;
import com.edumentor.review.entity.OutboxEvent;
import com.edumentor.review.exception.ApiException;
import com.edumentor.review.repository.OutboxEventRepository;
import com.edumentor.review.repository.RatingRepository;
import com.edumentor.review.repository.ReviewRepository;
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

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewFlowIntegrationTest {

    private static final String SECRET = "test-only-secret-key-that-is-at-least-32-bytes-long";
    private static final String AUTH = "Authorization";
    private static final long MENTOR_PROFILE = 7;
    private static final long STUDENT_A = 200;
    private static final long STUDENT_B = 201;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private RatingRepository ratingRepository;
    @Autowired
    private OutboxEventRepository outboxRepository;

    @MockBean
    private BookingClient bookingClient;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        reviewRepository.deleteAll();
        ratingRepository.deleteAll();
        when(bookingClient.getBooking(eq(50L), anyString(), any()))
                .thenReturn(new BookingInfo(50L, STUDENT_A, MENTOR_PROFILE, "COMPLETED"));
        when(bookingClient.getBooking(eq(51L), anyString(), any()))
                .thenReturn(new BookingInfo(51L, STUDENT_B, MENTOR_PROFILE, "COMPLETED"));
    }

    @Test
    void studentReviewsACompletedSessionAndTheRatingEventIsQueued() throws Exception {
        review(STUDENT_A, "{\"bookingId\":50,\"rating\":5,\"comment\":\"  Very helpful with branch choice  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.mentorId").value((int) MENTOR_PROFILE))
                .andExpect(jsonPath("$.comment").value("Very helpful with branch choice"));

        mockMvc.perform(get("/api/reviews/mentor/" + MENTOR_PROFILE + "/summary")
                        .header(AUTH, bearer(STUDENT_B, "STUDENT")))
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.averageRating").value(5.0));
        assertThat(outboxRepository.findAll()).singleElement().satisfies(e -> {
            assertThat(e.getMessageKey()).isEqualTo("7");
            assertThat(e.getPayload()).contains("MENTOR_RATING_UPDATED").contains("\"reviewCount\":1")
                    .contains("\"averageRating\":5.00");
        });
    }

    @Test
    void secondReviewForTheSameBookingIsRejected() throws Exception {
        review(STUDENT_A, "{\"bookingId\":50,\"rating\":4}").andExpect(status().isCreated());

        review(STUDENT_A, "{\"bookingId\":50,\"rating\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("REVIEW_ALREADY_EXISTS"));

        assertThat(reviewRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void averageIsComputedAcrossReviewsAndTheLatestEventCarriesTheTotals() throws Exception {
        review(STUDENT_A, "{\"bookingId\":50,\"rating\":5}").andExpect(status().isCreated());
        review(STUDENT_B, "{\"bookingId\":51,\"rating\":4}").andExpect(status().isCreated());

        mockMvc.perform(get("/api/reviews/mentor/" + MENTOR_PROFILE + "/summary")
                        .header(AUTH, bearer(STUDENT_A, "STUDENT")))
                .andExpect(jsonPath("$.reviewCount").value(2))
                .andExpect(jsonPath("$.averageRating").value(4.5));
        List<OutboxEvent> events = outboxRepository.findAll().stream()
                .sorted(Comparator.comparing(OutboxEvent::getId)).toList();
        assertThat(events).hasSize(2);
        assertThat(events.get(1).getPayload()).contains("\"reviewCount\":2").contains("\"averageRating\":4.50");
    }

    @Test
    void onlyCompletedBookingsCanBeReviewed() throws Exception {
        when(bookingClient.getBooking(eq(50L), anyString(), any()))
                .thenReturn(new BookingInfo(50L, STUDENT_A, MENTOR_PROFILE, "CONFIRMED"));

        review(STUDENT_A, "{\"bookingId\":50,\"rating\":5}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_COMPLETED"));
        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    void someoneElsesBookingLooksLikeItDoesNotExist() throws Exception {
        review(STUDENT_B, "{\"bookingId\":50,\"rating\":5}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    void ratingAndCommentAreValidated() throws Exception {
        review(STUDENT_A, "{\"bookingId\":50,\"rating\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rating").exists());
        review(STUDENT_A, "{\"bookingId\":50,\"rating\":6}")
                .andExpect(status().isBadRequest());
        review(STUDENT_A, "{\"rating\":5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.bookingId").exists());
        review(STUDENT_A, "{\"bookingId\":50,\"rating\":5,\"comment\":\"" + "x".repeat(1001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.comment").exists());
        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    void mentorsAndAnonymousUsersCannotPostReviews() throws Exception {
        review(100, "MENTOR", "{\"bookingId\":50,\"rating\":5}").andExpect(status().isForbidden());
        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":50,\"rating\":5}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicListHidesTheReviewerAndMyReviewsShowsOnlyMine() throws Exception {
        review(STUDENT_A, "{\"bookingId\":50,\"rating\":5,\"comment\":\"Great\"}").andExpect(status().isCreated());
        review(STUDENT_B, "{\"bookingId\":51,\"rating\":3}").andExpect(status().isCreated());

        mockMvc.perform(get("/api/reviews/mentor/" + MENTOR_PROFILE).header(AUTH, bearer(STUDENT_A, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].studentId").doesNotExist())
                .andExpect(jsonPath("$.content[0].bookingId").doesNotExist());
        mockMvc.perform(get("/api/reviews/me").header(AUTH, bearer(STUDENT_A, "STUDENT")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].bookingId").value(50));
        mockMvc.perform(get("/api/reviews/mentor/999/summary").header(AUTH, bearer(STUDENT_A, "STUDENT")))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.averageRating").value(0.0));
    }

    @Test
    void bookingServiceOutageFailsClosedAfterRetrying() throws Exception {
        when(bookingClient.getBooking(eq(50L), anyString(), any()))
                .thenThrow(ApiException.bookingServiceUnavailable());

        review(STUDENT_A, "{\"bookingId\":50,\"rating\":5}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("BOOKING_SERVICE_UNAVAILABLE"));

        verify(bookingClient, times(3)).getBooking(eq(50L), anyString(), any());
        assertThat(reviewRepository.count()).isZero();
    }

    // ---------- helpers ----------

    private ResultActions review(long studentId, String body) throws Exception {
        return review(studentId, "STUDENT", body);
    }

    private ResultActions review(long userId, String role, String body) throws Exception {
        return mockMvc.perform(post("/api/reviews").header(AUTH, bearer(userId, role))
                .contentType(MediaType.APPLICATION_JSON).content(body));
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