package com.edumentor.booking.scheduler;

import com.edumentor.booking.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.booking.reminder-scheduler.enabled", havingValue = "true",
        matchIfMissing = true)
public class ReminderScheduler {

    private final ReminderService reminderService;

    @Scheduled(fixedDelayString = "${app.booking.reminder-scan-interval-ms:60000}")
    public void run() {
        int queued = reminderService.sendDueReminders();
        if (queued > 0) {
            log.info("Session reminders queued: {}", queued);
        }
    }
}