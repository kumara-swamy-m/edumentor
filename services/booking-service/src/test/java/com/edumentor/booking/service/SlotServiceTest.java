package com.edumentor.booking.service;

import com.edumentor.booking.config.BookingProperties;
import com.edumentor.booking.dto.CreateSlotRequest;
import com.edumentor.booking.dto.SlotResponse;
import com.edumentor.booking.entity.MentorSlot;
import com.edumentor.booking.entity.SlotStatus;
import com.edumentor.booking.exception.ApiException;
import com.edumentor.booking.repository.MentorSlotRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SlotServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");
    private static final Instant START = NOW.plus(Duration.ofDays(2));

    private final MentorSlotRepository repository = mock(MentorSlotRepository.class);
    private final MentorDirectory directory = mock(MentorDirectory.class);
    private final SlotService service = new SlotService(repository, directory,
            new BookingProperties(Duration.ofMinutes(10), 3, Duration.ofMinutes(30), 100),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void endBeforeStartIsRejectedWithoutCallingMentorService() {
        assertThatThrownBy(() -> service.createSlot(100L, "Bearer x", request(START, START.minusSeconds(60))))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_SLOT_TIME");
        verifyNoInteractions(directory);
    }

    @Test
    void tooShortAndTooLongSlotsAreRejected() {
        assertThatThrownBy(() -> service.createSlot(100L, "Bearer x", request(START, START.plusSeconds(600))))
                .hasFieldOrPropertyWithValue("code", "INVALID_SLOT_TIME");
        assertThatThrownBy(() -> service.createSlot(100L, "Bearer x", request(START, START.plusSeconds(3 * 3600))))
                .hasFieldOrPropertyWithValue("code", "INVALID_SLOT_TIME");
    }

    @Test
    void slotStartingTooSoonIsRejected() {
        Instant soon = NOW.plus(Duration.ofMinutes(10));

        assertThatThrownBy(() -> service.createSlot(100L, "Bearer x", request(soon, soon.plusSeconds(3600))))
                .hasFieldOrPropertyWithValue("code", "INVALID_SLOT_TIME");
    }

    @Test
    void unapprovedMentorCannotCreateSlot() {
        when(directory.requireApprovedOwnProfile("Bearer x", 100L)).thenThrow(ApiException.mentorNotApproved());

        assertThatThrownBy(() -> service.createSlot(100L, "Bearer x", request(START, START.plusSeconds(3600))))
                .hasFieldOrPropertyWithValue("code", "MENTOR_NOT_APPROVED");
        verify(repository, never()).save(any());
    }

    @Test
    void overlappingSlotIsRejected() {
        when(directory.requireApprovedOwnProfile("Bearer x", 100L))
                .thenReturn(new MentorDirectory.MentorIdentity(7L, 100L));
        when(repository.existsByMentorIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(7L), anyCollection(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.createSlot(100L, "Bearer x", request(START, START.plusSeconds(3600))))
                .hasFieldOrPropertyWithValue("code", "SLOT_OVERLAP");
        verify(repository, never()).save(any());
    }

    @Test
    void validSlotIsSavedAsAvailableForTheApprovedMentor() {
        when(directory.requireApprovedOwnProfile("Bearer x", 100L))
                .thenReturn(new MentorDirectory.MentorIdentity(7L, 100L));
        when(repository.existsByMentorIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                anyLong(), anyCollection(), any(), any())).thenReturn(false);
        when(repository.save(any(MentorSlot.class))).thenAnswer(inv -> {
            MentorSlot slot = inv.getArgument(0);
            slot.setId(9L);
            return slot;
        });

        SlotResponse response = service.createSlot(100L, "Bearer x", request(START, START.plusSeconds(3600)));

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.mentorId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(response.price()).isEqualByComparingTo("500.00");
    }

    private CreateSlotRequest request(Instant start, Instant end) {
        return new CreateSlotRequest(start, end, new BigDecimal("500.00"));
    }
}