package com.edumentor.booking.messaging;

import com.edumentor.booking.client.MentorClient;
import com.edumentor.booking.client.MentorPublicProfile;
import com.edumentor.booking.entity.Booking;
import com.edumentor.booking.entity.BookingStatus;
import com.edumentor.booking.entity.MentorSlot;
import com.edumentor.booking.entity.OutboxEvent;
import com.edumentor.booking.entity.OutboxEventType;
import com.edumentor.booking.entity.OutboxStatus;
import com.edumentor.booking.entity.SlotStatus;
import com.edumentor.booking.repository.BookingRepository;
import com.edumentor.booking.repository.MentorSlotRepository;
import com.edumentor.booking.repository.OutboxEventRepository;
import com.edumentor.booking.service.BookingService;
import com.edumentor.booking.service.HoldExpiryService;
import com.edumentor.booking.service.PaymentEventHandler;
import com.edumentor.booking.service.ReminderService;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class BookingEventsIntegrationTest {

    private static final long STUDENT = 200;

    @Autowired
    private PaymentEventHandler handler;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private HoldExpiryService holdExpiryService;
    @Autowired
    private ReminderService reminderService;
    @Autowired
    private OutboxRelay relay;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private MentorSlotRepository slotRepository;
    @Autowired
    private OutboxEventRepository outboxRepository;

    @MockBean
    private MentorClient mentorClient;
    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        bookingRepository.deleteAll();
        slotRepository.deleteAll();
        when(mentorClient.getApprovedMentor(eq(7L), anyString(), any())).thenReturn(new MentorPublicProfile(7L));
    }

    @Test
    void holdingASlotQueuesBookingCreated() {
        long bookingId = hold(slot(3 * 24 * 60));

        assertThat(eventsOf(OutboxEventType.BOOKING_CREATED)).singleElement().satisfies(e -> {
            assertThat(e.getAggregateId()).isEqualTo(String.valueOf(bookingId));
            assertThat(e.getMessageKey()).isEqualTo(String.valueOf(bookingId));
            assertThat(e.getPayload()).contains("\"studentId\":200");
        });
    }

    @Test
    void paymentSuccessConfirmsTheBookingAttachesAMeetingAndAnnouncesOnce() {
        long bookingId = hold(slot(3 * 24 * 60));

        handler.handle(success(bookingId, 9001, "corr-42"));
        handler.handle(success(bookingId, 9001, "corr-42"));

        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getPaymentId()).isEqualTo(9001L);
        assertThat(booking.getMeetingUrl()).isEqualTo("https://meet.edumentor.local/session-" + bookingId);
        assertThat(slotRepository.findById(booking.getSlotId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.BOOKED);
        assertThat(eventsOf(OutboxEventType.BOOKING_CONFIRMED)).singleElement().satisfies(e -> {
            assertThat(e.getPayload()).contains("session-" + bookingId);
            assertThat(e.getCorrelationId()).isEqualTo("corr-42");
        });
    }

    @Test
    void paymentFailureCancelsTheBookingAndAnnouncesIt() {
        long bookingId = hold(slot(3 * 24 * 60));

        handler.handle("{\"eventType\":\"PAYMENT_FAILED\",\"paymentId\":9001,\"bookingId\":" + bookingId
                + ",\"studentId\":200,\"reason\":\"Card declined\"}");

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(eventsOf(OutboxEventType.BOOKING_CANCELLED)).singleElement()
                .satisfies(e -> assertThat(e.getPayload()).contains("Card declined"));
    }

    @Test
    void paymentForAnExpiredBookingIsRejectedForRefund() {
        long bookingId = hold(slot(3 * 24 * 60));
        makeHoldExpired(bookingId);
        holdExpiryService.expireStaleHolds();

        handler.handle(success(bookingId, 9001, "corr-1"));

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.EXPIRED);
        assertThat(eventsOf(OutboxEventType.BOOKING_PAYMENT_REJECTED)).singleElement()
                .satisfies(e -> assertThat(e.getPayload()).contains("\"paymentId\":9001"));
        assertThat(eventsOf(OutboxEventType.BOOKING_CONFIRMED)).isEmpty();
    }

    @Test
    void secondPaymentForAnAlreadyConfirmedBookingIsRejectedForRefund() {
        long bookingId = hold(slot(3 * 24 * 60));
        handler.handle(success(bookingId, 9001, "corr-1"));

        handler.handle(success(bookingId, 9002, "corr-2"));

        assertThat(eventsOf(OutboxEventType.BOOKING_PAYMENT_REJECTED)).singleElement()
                .satisfies(e -> assertThat(e.getPayload()).contains("\"paymentId\":9002"));
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getPaymentId()).isEqualTo(9001L);
    }

    @Test
    void paymentForAnUnknownBookingIsRejectedForRefund() {
        handler.handle(success(99999, 9001, "corr-1"));

        assertThat(eventsOf(OutboxEventType.BOOKING_PAYMENT_REJECTED)).hasSize(1);
    }

    @Test
    void expiredHoldQueuesBookingCancelledWithStatusExpired() {
        long bookingId = hold(slot(3 * 24 * 60));
        makeHoldExpired(bookingId);

        holdExpiryService.expireStaleHolds();

        assertThat(eventsOf(OutboxEventType.BOOKING_CANCELLED)).singleElement()
                .satisfies(e -> assertThat(e.getPayload()).contains("\"status\":\"EXPIRED\""));
    }

    @Test
    void malformedAndUnrelatedEventsAreHandledSafely() {
        assertThatThrownBy(() -> handler.handle("not json")).isInstanceOf(IllegalArgumentException.class);
        handler.handle("{\"eventType\":\"PAYMENT_REFUNDED\",\"paymentId\":1}");

        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void reminderIsQueuedOnceForASessionStartingSoon() {
        long bookingId = hold(slot(3 * 24 * 60));
        handler.handle(success(bookingId, 9001, "corr-1"));
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setSessionStart(Instant.now().plus(30, ChronoUnit.MINUTES));
        bookingRepository.save(booking);

        assertThat(reminderService.sendDueReminders()).isEqualTo(1);
        assertThat(reminderService.sendDueReminders()).isZero();

        assertThat(eventsOf(OutboxEventType.SESSION_REMINDER)).singleElement()
                .satisfies(e -> assertThat(e.getPayload()).contains("session-" + bookingId));
    }

    @Test
    void sessionsFarInTheFutureGetNoReminderYet() {
        long bookingId = hold(slot(3 * 24 * 60));
        handler.handle(success(bookingId, 9001, "corr-1"));

        assertThat(reminderService.sendDueReminders()).isZero();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void relayPublishesToTheRightTopicsWithTheBookingIdAsKey() {
        long bookingId = hold(slot(3 * 24 * 60));
        handler.handle(success(bookingId, 9001, "corr-1"));
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setSessionStart(Instant.now().plus(30, ChronoUnit.MINUTES));
        bookingRepository.save(booking);
        reminderService.sendDueReminders();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        assertThat(relay.publishPending()).isEqualTo(3);

        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(3)).send(captor.capture());
        List<ProducerRecord> sent = captor.getAllValues();
        assertThat(sent).extracting(ProducerRecord::topic)
                .containsExactly("booking-events", "booking-events", "notification-events");
        assertThat(sent).extracting(ProducerRecord::key).containsOnly(String.valueOf(bookingId));
        assertThat(outboxRepository.findAll()).allSatisfy(e -> assertThat(e.getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED));
    }

    // ---------- helpers ----------

    private long slot(int startInMinutes) {
        MentorSlot slot = new MentorSlot();
        slot.setMentorId(7L);
        slot.setMentorUserId(100L);
        slot.setStartTime(Instant.now().plus(startInMinutes, ChronoUnit.MINUTES));
        slot.setEndTime(Instant.now().plus(startInMinutes + 60, ChronoUnit.MINUTES));
        slot.setPrice(new BigDecimal("500.00"));
        return slotRepository.save(slot).getId();
    }

    private long hold(long slotId) {
        return bookingService.createBooking(STUDENT, slotId, "Bearer x").id();
    }

    private void makeHoldExpired(long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        Instant past = Instant.now().minusSeconds(5);
        booking.setHoldExpiresAt(past);
        bookingRepository.save(booking);
        MentorSlot slot = slotRepository.findById(booking.getSlotId()).orElseThrow();
        slot.setHoldExpiresAt(past);
        slotRepository.save(slot);
    }

    private String success(long bookingId, long paymentId, String correlationId) {
        return "{\"eventType\":\"PAYMENT_SUCCESS\",\"correlationId\":\"" + correlationId + "\",\"paymentId\":"
                + paymentId + ",\"bookingId\":" + bookingId + ",\"studentId\":200}";
    }

    private List<OutboxEvent> eventsOf(OutboxEventType type) {
        return outboxRepository.findAll().stream().filter(e -> e.getEventType() == type).toList();
    }
}