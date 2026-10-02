package com.edumentor.booking.messaging;

import com.edumentor.booking.service.PaymentEventHandler;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentEventListener {

    private final PaymentEventHandler handler;

    @KafkaListener(topics = Topics.PAYMENT_EVENTS)
    public void onMessage(ConsumerRecord<String, String> record) {
        if (record.value() != null) {
            handler.handle(record.value());
        }
    }
}