package com.edumentor.booking.service;

import com.edumentor.booking.config.BookingProperties;
import com.edumentor.booking.dto.CreateSlotRequest;
import com.edumentor.booking.dto.PageResponse;
import com.edumentor.booking.dto.SlotResponse;
import com.edumentor.booking.entity.MentorSlot;
import com.edumentor.booking.entity.SlotStatus;
import com.edumentor.booking.exception.ApiException;
import com.edumentor.booking.repository.MentorSlotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class SlotService {

    private static final Duration MIN_DURATION = Duration.ofMinutes(15);
    private static final Duration MAX_DURATION = Duration.ofMinutes(120);
    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<SlotStatus> ACTIVE_STATUSES = Set.of(SlotStatus.AVAILABLE, SlotStatus.HELD,
            SlotStatus.BOOKED);

    private final MentorSlotRepository slotRepository;
    private final MentorDirectory mentorDirectory;
    private final BookingProperties properties;
    private final Clock clock;

    public SlotService(MentorSlotRepository slotRepository, MentorDirectory mentorDirectory,
                       BookingProperties properties, Clock clock) {
        this.slotRepository = slotRepository;
        this.mentorDirectory = mentorDirectory;
        this.properties = properties;
        this.clock = clock;
    }

    /** Local validation first, then the remote approval check, then a single save. */
    public SlotResponse createSlot(Long userId, String authorization, CreateSlotRequest request) {
        validateTimes(request.startTime(), request.endTime());
        MentorDirectory.MentorIdentity mentor = mentorDirectory.requireApprovedOwnProfile(authorization, userId);

        if (slotRepository.existsByMentorIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                mentor.mentorId(), ACTIVE_STATUSES, request.endTime(), request.startTime())) {
            throw ApiException.slotOverlap();
        }

        MentorSlot slot = new MentorSlot();
        slot.setMentorId(mentor.mentorId());
        slot.setMentorUserId(mentor.userId());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setPrice(request.price());
        slot.setStatus(SlotStatus.AVAILABLE);
        slot = slotRepository.save(slot);
        log.info("Slot created: slotId={}, mentorId={}", slot.getId(), slot.getMentorId());
        return SlotResponse.from(slot);
    }

    @Transactional(readOnly = true)
    public PageResponse<SlotResponse> listOwnSlots(Long userId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "startTime"));
        return PageResponse.from(slotRepository.findByMentorUserId(userId, pageable), SlotResponse::from);
    }

    /** Students see only future AVAILABLE slots of a mentor who is currently approved. */
    public List<SlotResponse> listAvailableSlots(Long mentorId, String authorization) {
        mentorDirectory.requireApprovedMentor(mentorId, authorization);
        return slotRepository.findByMentorIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(
                        mentorId, SlotStatus.AVAILABLE, Instant.now(clock)).stream()
                .map(SlotResponse::from)
                .toList();
    }

    @Transactional
    public SlotResponse cancelSlot(Long userId, Long slotId) {
        MentorSlot slot = slotRepository.findByIdForUpdate(slotId).orElseThrow(ApiException::slotNotFound);
        if (!slot.getMentorUserId().equals(userId)) {
            throw ApiException.slotNotFound();
        }
        if (slot.getStatus() == SlotStatus.CANCELLED) {
            return SlotResponse.from(slot);
        }
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw ApiException.slotNotCancellable();
        }
        slot.setStatus(SlotStatus.CANCELLED);
        log.info("Slot cancelled by mentor: slotId={}", slotId);
        return SlotResponse.from(slot);
    }

    /** Marks AVAILABLE slots whose start time has passed as EXPIRED. */
    @Transactional
    public int expirePastSlots() {
        int updated = slotRepository.expirePastAvailable(SlotStatus.AVAILABLE, SlotStatus.EXPIRED,
                Instant.now(clock));
        if (updated > 0) {
            log.info("Expired {} past available slots", updated);
        }
        return updated;
    }

    private void validateTimes(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw ApiException.invalidSlotTime("End time must be after start time");
        }
        Duration duration = Duration.between(start, end);
        if (duration.compareTo(MIN_DURATION) < 0 || duration.compareTo(MAX_DURATION) > 0) {
            throw ApiException.invalidSlotTime("A slot must last between 15 and 120 minutes");
        }
        if (start.isBefore(Instant.now(clock).plus(properties.minSlotLeadTime()))) {
            throw ApiException.invalidSlotTime("A slot must start at least "
                    + properties.minSlotLeadTime().toMinutes() + " minutes from now");
        }
    }
}