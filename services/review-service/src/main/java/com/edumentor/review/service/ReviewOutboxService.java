package com.edumentor.review.service;

import com.edumentor.review.entity.OutboxEvent;
import com.edumentor.review.entity.OutboxEventType;
import com.edumentor.review.entity.Rating;
import com.edumentor.review.entity.Review;
import com.edumentor.review.event.MentorRatingUpdatedEvent;
import com.edumentor.review.repository.OutboxEventRepository;
import com.edumentor.review.web.CorrelationIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Must run inside the transaction that saves the review and updates the aggregate. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ReviewOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public void ratingUpdated(Review review, Rating rating) {
        String eventId = UUID.randomUUID().toString();
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        MentorRatingUpdatedEvent payload = new MentorRatingUpdatedEvent(eventId,
                OutboxEventType.MENTOR_RATING_UPDATED.name(), clock.instant(), correlationId,
                rating.getMentorId(), review.getId(), review.getBookingId(), rating.average(),
                rating.getRatingCount());

        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setAggregateType("MENTOR");
        event.setAggregateId(String.valueOf(rating.getMentorId()));
        event.setMessageKey(String.valueOf(rating.getMentorId()));
        event.setEventType(OutboxEventType.MENTOR_RATING_UPDATED);
        event.setCorrelationId(correlationId);
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize outbox event", ex);
        }
        outboxEventRepository.save(event);
        log.info("Outbox event recorded: type=MENTOR_RATING_UPDATED, mentorId={}, reviewCount={}, eventId={}",
                rating.getMentorId(), rating.getRatingCount(), eventId);
    }
}