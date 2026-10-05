package com.edumentor.review.messaging;

import com.edumentor.review.entity.OutboxEvent;
import com.edumentor.review.entity.OutboxStatus;
import com.edumentor.review.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** Publishes up to one batch of pending events in id order; stops at the first failure. */
    public int publishPending() {
        int published = 0;
        for (OutboxEvent event : outboxEventRepository.findTop100ByStatusOrderByIdAsc(OutboxStatus.PENDING)) {
            String key = event.getMessageKey() != null ? event.getMessageKey() : event.getAggregateId();
            ProducerRecord<String, String> record = new ProducerRecord<>(event.getEventType().topic(), key,
                    event.getPayload());
            record.headers().add("eventType", event.getEventType().name().getBytes(StandardCharsets.UTF_8));
            record.headers().add("eventId", event.getEventId().getBytes(StandardCharsets.UTF_8));
            if (event.getCorrelationId() != null) {
                record.headers().add("correlationId", event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
            }
            try {
                kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return published;
            } catch (ExecutionException | TimeoutException ex) {
                log.warn("Kafka publish failed, will retry: eventId={}, cause={}", event.getEventId(),
                        ex.getClass().getSimpleName());
                return published;
            }
            Long id = event.getId();
            transactionTemplate.executeWithoutResult(status -> outboxEventRepository.findById(id).ifPresent(row -> {
                row.setStatus(OutboxStatus.PUBLISHED);
                row.setPublishedAt(Instant.now(clock));
            }));
            log.info("Kafka event published: type={}, eventId={}, topic={}", event.getEventType(),
                    event.getEventId(), event.getEventType().topic());
            published++;
        }
        return published;
    }
}