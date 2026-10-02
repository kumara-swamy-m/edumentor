package com.edumentor.booking.controller;

import com.edumentor.booking.client.MentorClient;
import com.edumentor.booking.client.MentorOwnProfile;
import com.edumentor.booking.client.MentorPublicProfile;
import com.edumentor.booking.entity.Booking;
import com.edumentor.booking.entity.BookingStatus;
import com.edumentor.booking.entity.MentorSlot;
import com.edumentor.booking.entity.SlotStatus;
import com.edumentor.booking.exception.ApiException;
import com.edumentor.booking.repository.BookingRepository;
import com.edumentor.booking.repository.MentorSlotRepository;
import com.edumentor.booking.service.BookingService;
import com.edumentor.booking.service.HoldExpiryService;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingFlowIntegrationTest {

    private static final String SECRET = "test-only-secret-key-that-is-at-least-32-bytes-long";
    private static final String AUTH = "Authorization";
    private static final long MENTOR_PROFILE = 7;
    private static final long MENTOR_USER = 100;
    private static final long OTHER_MENTOR_USER = 101;
    private static final long STUDENT_A = 200;
    private static final long STUDENT_B = 201;
    private static final long ADMIN = 1;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private MentorSlotRepository slotRepository;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private HoldExpiryService holdExpiryService;
    @Autowired
    private com.edumentor.booking.service.SlotService slotService;

    @MockBean
    private MentorClient mentorClient;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        slotRepository.deleteAll();
        when(mentorClient.getOwnProfile(anyString(), any()))
                .thenReturn(new MentorOwnProfile(MENTOR_PROFILE, MENTOR_USER, "APPROVED"));
        when(mentorClient.getApprovedMentor(eq(MENTOR_PROFILE), anyString(), any()))
                .thenReturn(new MentorPublicProfile(MENTOR_PROFILE));
    }

    // ---------- Slot management ----------

    @Test
    void mentorCreatesSlotAndStudentsSeeIt() throws Exception {
        long slotId = createSlot(48 * 60, 60);

        mockMvc.perform(get("/api/bookings/mentors/" + MENTOR_PROFILE + "/slots")
                        .header(AUTH, bearer(STUDENT_A, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value((int) slotId))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    void studentCannotCreateSlotAndUnauthenticatedIsRejected() throws Exception {
        mockMvc.perform(post("/api/bookings/slots").header(AUTH, bearer(STUDENT_A, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(slotBody(48 * 60, 60))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/bookings/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unapprovedMentorCannotCreateSlot() throws Exception {
        when(mentorClient.getOwnProfile(anyString(), any()))
                .thenReturn(new MentorOwnProfile(MENTOR_PROFILE, MENTOR_USER, "PENDING"));

        mockMvc.perform(post("/api/bookings/slots").header(AUTH, bearer(MENTOR_USER, "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(slotBody(48 * 60, 60))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("MENTOR_NOT_APPROVED"));
    }

    @Test
    void overlappingAndInvalidSlotsAreRejected() throws Exception {
        createSlot(48 * 60, 60);

        mockMvc.perform(post("/api/bookings/slots").header(AUTH, bearer(MENTOR_USER, "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(slotBody(48 * 60 + 30, 60))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLOT_OVERLAP"));

        Map<String, Object> endBeforeStart = slotBody(72 * 60, 60);
        endBeforeStart.put("endTime", Instant.now().plus(71 * 60, ChronoUnit.MINUTES).toString());
        mockMvc.perform(post("/api/bookings/slots").header(AUTH, bearer(MENTOR_USER, "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(endBeforeStart)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SLOT_TIME"));

        Map<String, Object> inThePast = slotBody(-120, 60);
        mockMvc.perform(post("/api/bookings/slots").header(AUTH, bearer(MENTOR_USER, "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(inThePast)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.startTime").exists());
    }

    @Test
    void mentorCancelsOnlyOwnAvailableSlots() throws Exception {
        long slotId = createSlot(48 * 60, 60);

        mockMvc.perform(delete("/api/bookings/slots/" + slotId).header(AUTH, bearer(OTHER_MENTOR_USER, "MENTOR")))
                .andExpect(status().isNotFound());

        long heldSlot = createSlot(72 * 60, 60);
        hold(STUDENT_A, heldSlot).andExpect(status().isCreated());
        mockMvc.perform(delete("/api/bookings/slots/" + heldSlot).header(AUTH, bearer(MENTOR_USER, "MENTOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLOT_NOT_CANCELLABLE"));

        mockMvc.perform(delete("/api/bookings/slots/" + slotId).header(AUTH, bearer(MENTOR_USER, "MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ---------- Holding ----------

    @Test
    void studentHoldsSlotAndSecondStudentIsRejected() throws Exception {
        long slotId = createSlot(48 * 60, 60);

        hold(STUDENT_A, slotId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.studentId").value((int) STUDENT_A))
                .andExpect(jsonPath("$.holdExpiresAt").isNotEmpty());

        hold(STUDENT_B, slotId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLOT_NOT_AVAILABLE"));

        mockMvc.perform(get("/api/bookings/mentors/" + MENTOR_PROFILE + "/slots")
                        .header(AUTH, bearer(STUDENT_B, "STUDENT")))
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(slotRepository.findById(slotId).orElseThrow().getStatus()).isEqualTo(SlotStatus.HELD);
    }

    @Test
    void holdIsRejectedWhenMentorIsNoLongerApproved() throws Exception {
        long slotId = createSlot(48 * 60, 60);
        when(mentorClient.getApprovedMentor(eq(MENTOR_PROFILE), anyString(), any()))
                .thenThrow(ApiException.mentorNotFound());

        hold(STUDENT_A, slotId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("MENTOR_NOT_FOUND"));
        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void holdFailsClosedWhenMentorServiceIsDown() throws Exception {
        long slotId = createSlot(48 * 60, 60);
        when(mentorClient.getApprovedMentor(eq(MENTOR_PROFILE), anyString(), any()))
                .thenThrow(ApiException.mentorServiceUnavailable());

        hold(STUDENT_A, slotId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("MENTOR_SERVICE_UNAVAILABLE"));
        assertThat(bookingRepository.count()).isZero();
        assertThat(slotRepository.findById(slotId).orElseThrow().getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void studentCannotHoldMoreThanThreeUnpaidSlots() throws Exception {
        for (int i = 1; i <= 3; i++) {
            hold(STUDENT_A, createSlot(48 * 60 + i * 120, 60)).andExpect(status().isCreated());
        }
        long fourth = createSlot(48 * 60 + 4 * 120, 60);

        hold(STUDENT_A, fourth)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("TOO_MANY_PENDING_BOOKINGS"));
    }

    @Test
    void mentorCannotHoldSlots() throws Exception {
        long slotId = createSlot(48 * 60, 60);

        mockMvc.perform(post("/api/bookings").header(AUTH, bearer(MENTOR_USER, "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("slotId", slotId))))
                .andExpect(status().isForbidden());
    }

    // ---------- Cancel and expiry ----------

    @Test
    void studentCancelsPendingBookingAndSlotBecomesAvailableAgain() throws Exception {
        long slotId = createSlot(48 * 60, 60);
        long bookingId = holdAndGetId(STUDENT_A, slotId);

        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel").header(AUTH, bearer(STUDENT_A, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(slotRepository.findById(slotId).orElseThrow().getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getActiveSlotId()).isNull();
        hold(STUDENT_B, slotId).andExpect(status().isCreated());
    }

    @Test
    void expiredHoldIsReleasedByTheSweep() throws Exception {
        long slotId = createSlot(48 * 60, 60);
        long bookingId = holdAndGetId(STUDENT_A, slotId);
        makeHoldExpired(bookingId);

        assertThat(holdExpiryService.expireStaleHolds()).isEqualTo(1);

        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(booking.getActiveSlotId()).isNull();
        assertThat(slotRepository.findById(slotId).orElseThrow().getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        hold(STUDENT_B, slotId).andExpect(status().isCreated());
    }

    @Test
    void expiredButUnsweptHoldCanBeTakenOverImmediately() throws Exception {
        long slotId = createSlot(48 * 60, 60);
        long firstBooking = holdAndGetId(STUDENT_A, slotId);
        makeHoldExpired(firstBooking);

        hold(STUDENT_B, slotId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value((int) STUDENT_B));

        assertThat(bookingRepository.findById(firstBooking).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.EXPIRED);
    }

    // ---------- Payment outcomes (saga hooks) ----------

    @Test
    void paymentConfirmationBooksTheSlotAndIsIdempotent() throws Exception {
        long slotId = createSlot(48 * 60, 60);
        long bookingId = holdAndGetId(STUDENT_A, slotId);

        assertThat(bookingService.confirmPayment(bookingId, 9001L).status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(bookingService.confirmPayment(bookingId, 9001L).status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThatThrownBy(() -> bookingService.confirmPayment(bookingId, 9002L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "BOOKING_NOT_PENDING");

        assertThat(slotRepository.findById(slotId).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel").header(AUTH, bearer(STUDENT_A, "STUDENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_CANCELLABLE"));
        hold(STUDENT_B, slotId).andExpect(status().isConflict());
    }

    @Test
    void paymentFailureCancelsBookingAndReleasesSlot() throws Exception {
        long slotId = createSlot(48 * 60, 60);
        long bookingId = holdAndGetId(STUDENT_A, slotId);

        assertThat(bookingService.cancelAfterPaymentFailure(bookingId, "Card declined").status())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingService.cancelAfterPaymentFailure(bookingId, "Card declined").status())
                .isEqualTo(BookingStatus.CANCELLED);

        assertThat(slotRepository.findById(slotId).orElseThrow().getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThatThrownBy(() -> bookingService.confirmPayment(bookingId, 9001L))
                .hasFieldOrPropertyWithValue("code", "BOOKING_NOT_PENDING");
    }

    @Test
    void lateConfirmationOfAnExpiredHoldIsRejected() throws Exception {
        long slotId = createSlot(48 * 60, 60);
        long bookingId = holdAndGetId(STUDENT_A, slotId);
        makeHoldExpired(bookingId);
        holdExpiryService.expireStaleHolds();

        assertThatThrownBy(() -> bookingService.confirmPayment(bookingId, 9001L))
                .hasFieldOrPropertyWithValue("code", "BOOKING_NOT_PENDING");
    }

    // ---------- Completion ----------

    @Test
    void mentorCompletesConfirmedBookingOnlyAfterSessionStart() throws Exception {
        long slotId = createSlot(48 * 60, 60);
        long bookingId = holdAndGetId(STUDENT_A, slotId);
        bookingService.confirmPayment(bookingId, 9001L);

        mockMvc.perform(post("/api/bookings/" + bookingId + "/complete").header(AUTH, bearer(STUDENT_A, "STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/bookings/" + bookingId + "/complete").header(AUTH, bearer(MENTOR_USER, "MENTOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SESSION_NOT_STARTED"));

        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setSessionStart(Instant.now().minus(1, ChronoUnit.HOURS));
        bookingRepository.save(booking);

        mockMvc.perform(post("/api/bookings/" + bookingId + "/complete").header(AUTH, bearer(MENTOR_USER, "MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // ---------- Visibility ----------

    @Test
    void bookingsAreVisibleOnlyToParticipantsAndAdmins() throws Exception {
        long slotId = createSlot(48 * 60, 60);
        long bookingId = holdAndGetId(STUDENT_A, slotId);

        mockMvc.perform(get("/api/bookings/" + bookingId).header(AUTH, bearer(STUDENT_B, "STUDENT")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/bookings/" + bookingId).header(AUTH, bearer(STUDENT_A, "STUDENT")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/bookings/" + bookingId).header(AUTH, bearer(MENTOR_USER, "MENTOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/bookings/" + bookingId).header(AUTH, bearer(ADMIN, "ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bookings/me").header(AUTH, bearer(STUDENT_A, "STUDENT")))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/bookings/me").header(AUTH, bearer(MENTOR_USER, "MENTOR")))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/bookings/me").header(AUTH, bearer(STUDENT_B, "STUDENT")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void pastAvailableSlotsAreMarkedExpired() throws Exception {
        MentorSlot slot = new MentorSlot();
        slot.setMentorId(MENTOR_PROFILE);
        slot.setMentorUserId(MENTOR_USER);
        slot.setStartTime(Instant.now().minus(2, ChronoUnit.HOURS));
        slot.setEndTime(Instant.now().minus(1, ChronoUnit.HOURS));
        slot.setPrice(new java.math.BigDecimal("500.00"));
        slot = slotRepository.save(slot);

        mockMvc.perform(get("/api/bookings/slots/mine").header(AUTH, bearer(MENTOR_USER, "MENTOR")))
                .andExpect(jsonPath("$.content[0].status").value("AVAILABLE"));
        long id = slot.getId();
        assertThat(slotService.expirePastSlots()).isEqualTo(1);
        assertThat(slotRepository.findById(id).orElseThrow().getStatus()).isEqualTo(SlotStatus.EXPIRED);
    }

    // ---------- helpers ----------

    private long createSlot(int startInMinutes, int durationMinutes) throws Exception {
        String response = mockMvc.perform(post("/api/bookings/slots").header(AUTH, bearer(MENTOR_USER, "MENTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(slotBody(startInMinutes, durationMinutes))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private ResultActions hold(long studentId, long slotId) throws Exception {
        return mockMvc.perform(post("/api/bookings").header(AUTH, bearer(studentId, "STUDENT"))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("slotId", slotId))));
    }

    private long holdAndGetId(long studentId, long slotId) throws Exception {
        String response = hold(studentId, slotId).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    /** Pushes the hold deadline into the past on both the booking and the slot. */
    private void makeHoldExpired(long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        Instant past = Instant.now().minusSeconds(5);
        booking.setHoldExpiresAt(past);
        bookingRepository.save(booking);
        MentorSlot slot = slotRepository.findById(booking.getSlotId()).orElseThrow();
        slot.setHoldExpiresAt(past);
        slotRepository.save(slot);
    }

    private Map<String, Object> slotBody(int startInMinutes, int durationMinutes) {
        Instant start = Instant.now().plus(startInMinutes, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startTime", start.toString());
        body.put("endTime", start.plus(durationMinutes, ChronoUnit.MINUTES).toString());
        body.put("price", 500.00);
        return body;
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

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}