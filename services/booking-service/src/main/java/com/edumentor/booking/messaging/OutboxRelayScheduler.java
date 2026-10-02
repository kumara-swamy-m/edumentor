package com.edumentor.booking.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {

    private final OutboxRelay relay;

    @Scheduled(fixedDelayString = "${app.outbox.relay-interval-ms:1000}")
    public void run() {
        try {
            relay.publishPending();
        } catch (RuntimeException ex) {
            log.warn("Outbox relay run failed: {}", ex.getClass().getSimpleName());
        }
    }
}