package com.edumentor.booking.controller;

import com.edumentor.booking.client.MentorClient;
import com.edumentor.booking.client.MentorPublicProfile;
import com.edumentor.booking.entity.MentorSlot;
import com.edumentor.booking.entity.SlotStatus;
import com.edumentor.booking.exception.ApiException;
import com.edumentor.booking.repository.BookingRepository;
import com.edumentor.booking.repository.MentorSlotRepository;
import com.edumentor.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
class SlotConcurrencyIntegrationTest {

    private static final int STUDENTS = 8;

    @Autowired
    private BookingService bookingService;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private MentorSlotRepository slotRepository;

    @MockBean
    private MentorClient mentorClient;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        slotRepository.deleteAll();
        when(mentorClient.getApprovedMentor(eq(7L), anyString(), any())).thenReturn(new MentorPublicProfile(7L));
    }

    @Test
    void onlyOneOfManyConcurrentStudentsCanHoldASlot() throws Exception {
        MentorSlot slot = new MentorSlot();
        slot.setMentorId(7L);
        slot.setMentorUserId(100L);
        slot.setStartTime(Instant.now().plus(2, ChronoUnit.DAYS));
        slot.setEndTime(Instant.now().plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
        slot.setPrice(new BigDecimal("500.00"));
        long slotId = slotRepository.save(slot).getId();

        ExecutorService pool = Executors.newFixedThreadPool(STUDENTS);
        CountDownLatch ready = new CountDownLatch(STUDENTS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < STUDENTS; i++) {
                long studentId = 1000L + i;
                Callable<String> task = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        bookingService.createBooking(studentId, slotId, "Bearer test");
                        return "HELD";
                    } catch (ApiException ex) {
                        return ex.getCode();
                    }
                };
                futures.add(pool.submit(task));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();

            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn("HELD"::equals).hasSize(1);
            assertThat(results).filteredOn(r -> !"HELD".equals(r)).hasSize(STUDENTS - 1)
                    .containsOnly("SLOT_NOT_AVAILABLE");
        } finally {
            pool.shutdownNow();
        }

        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(slotRepository.findById(slotId).orElseThrow().getStatus()).isEqualTo(SlotStatus.HELD);
    }
}