package com.edumentor.payment.messaging;

import com.edumentor.payment.entity.OutboxEvent;
import com.edumentor.payment.entity.OutboxEventType;
import com.edumentor.payment.entity.OutboxStatus;
import com.edumentor.payment.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class OutboxRelayIntegrationTest {

    @Autowired
    private OutboxRelay relay;
    @Autowired
    private OutboxEventRepository repository;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void publishesPendingEventsInOrderUsingTheBookingIdAsKey() {
        save("50", "7");
        save(null, "8");
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        assertThat(relay.publishPending()).isEqualTo(2);

        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(captor.capture());
        List<ProducerRecord> sent = captor.getAllValues();
        assertThat(sent.get(0).topic()).isEqualTo("payment-events");
        assertThat(sent.get(0).key()).isEqualTo("50");
        assertThat(sent.get(1).key()).isEqualTo("8");
        assertThat(repository.findAll()).allSatisfy(e -> {
            assertThat(e.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(e.getPublishedAt()).isNotNull();
        });
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void failedPublishLeavesTheEventPendingForTheNextRun() {
        save("50", "7");
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThat(relay.publishPending()).isZero();

        assertThat(repository.findAll()).singleElement()
                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(OutboxStatus.PENDING));
    }

    private void save(String messageKey, String aggregateId) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setAggregateType("PAYMENT");
        event.setAggregateId(aggregateId);
        event.setMessageKey(messageKey);
        event.setEventType(OutboxEventType.PAYMENT_SUCCESS);
        event.setPayload("{}");
        repository.save(event);
    }
}