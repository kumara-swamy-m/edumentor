package com.edumentor.review.messaging;

import com.edumentor.review.entity.OutboxEvent;
import com.edumentor.review.entity.OutboxEventType;
import com.edumentor.review.entity.OutboxStatus;
import com.edumentor.review.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    void publishesToReviewEventsKeyedByMentorAndMarksPublished() {
        save();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        assertThat(relay.publishPending()).isEqualTo(1);

        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("review-events");
        assertThat(captor.getValue().key()).isEqualTo("7");
        assertThat(repository.findAll()).singleElement()
                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(OutboxStatus.PUBLISHED));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void failedPublishStaysPending() {
        save();
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThat(relay.publishPending()).isZero();
        assertThat(repository.findAll()).singleElement()
                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(OutboxStatus.PENDING));
    }

    private void save() {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setAggregateType("MENTOR");
        event.setAggregateId("7");
        event.setMessageKey("7");
        event.setEventType(OutboxEventType.MENTOR_RATING_UPDATED);
        event.setPayload("{}");
        repository.save(event);
    }
}