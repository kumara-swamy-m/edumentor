package com.edumentor.booking.scheduler;

import com.edumentor.booking.service.HoldExpiryService;
import com.edumentor.booking.service.SlotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.booking.expiry-scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class HoldExpiryScheduler {

    private final HoldExpiryService holdExpiryService;
    private final SlotService slotService;

    @Scheduled(fixedDelayString = "${app.booking.expiry-scan-interval-ms:30000}")
    public void sweep() {
        int holds = holdExpiryService.expireStaleHolds();
        int slots = slotService.expirePastSlots();
        if (holds > 0) {
            log.info("Hold sweep finished: expiredHolds={}, expiredSlots={}", holds, slots);
        }
    }
}