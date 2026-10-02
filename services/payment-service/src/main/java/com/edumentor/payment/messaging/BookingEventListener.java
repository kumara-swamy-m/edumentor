package com.edumentor.payment.messaging;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class BookingEventListener {

    private final BookingEventHandler handler;

    @KafkaListener(topics = Topics.BOOKING_EVENTS)
    public void onMessage(ConsumerRecord<String, String> record) {
        if (record.value() != null) {
            handler.handle(record.value());
        }
    }
}