package com.edumentor.notification.messaging;

import com.edumentor.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationEventListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = {Topics.PAYMENT_EVENTS, Topics.BOOKING_EVENTS, Topics.NOTIFICATION_EVENTS})
    public void onMessage(ConsumerRecord<String, String> record) {
        if (record.value() != null) {
            notificationService.handleEvent(record.value());
        }
    }
}