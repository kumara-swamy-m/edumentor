package com.edumentor.booking.service;

import com.edumentor.booking.config.BookingProperties;
import com.edumentor.booking.entity.BookingStatus;
import com.edumentor.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldExpiryService {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final BookingProperties properties;
    private final Clock clock;

    /** Expires up to one batch of stale holds and returns how many were expired. */
    public int expireStaleHolds() {
        List<Long> ids = bookingRepository.findExpiredHoldIds(BookingStatus.PENDING_PAYMENT,
                Instant.now(clock), PageRequest.of(0, properties.expiryBatchSize()));
        int expired = 0;
        for (Long id : ids) {
            try {
                if (bookingService.expireHold(id)) {
                    expired++;
                }
            } catch (RuntimeException ex) {
                log.warn("Could not expire hold for bookingId={}: {}", id, ex.getClass().getSimpleName());
            }
        }
        return expired;
    }
}