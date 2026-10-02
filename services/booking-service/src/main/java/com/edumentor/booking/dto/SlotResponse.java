package com.edumentor.booking.dto;

import com.edumentor.booking.entity.MentorSlot;
import com.edumentor.booking.entity.SlotStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record SlotResponse(Long id, Long mentorId, Instant startTime, Instant endTime, BigDecimal price,
                           SlotStatus status) {

    public static SlotResponse from(MentorSlot slot) {
        return new SlotResponse(slot.getId(), slot.getMentorId(), slot.getStartTime(), slot.getEndTime(),
                slot.getPrice(), slot.getStatus());
    }
}