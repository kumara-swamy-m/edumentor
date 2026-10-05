package com.edumentor.mentor.messaging;

import com.edumentor.mentor.service.MentorRatingService;
import com.edumentor.mentor.web.CorrelationIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RatingEventHandler {

    private final MentorRatingService ratingService;
    private final ObjectMapper objectMapper;

    public void handle(String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Malformed review event", ex);
        }
        String correlationId = root.path("correlationId").asText(null);
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        }
        try {
            if (!"MENTOR_RATING_UPDATED".equals(root.path("eventType").asText(""))) {
                log.debug("Review event ignored");
                return;
            }
            long mentorId = root.path("mentorId").asLong(0);
            int reviewCount = root.path("reviewCount").asInt(-1);
            double average = root.path("averageRating").asDouble(-1);
            if (mentorId <= 0 || reviewCount < 0 || average < 0 || average > 5) {
                throw new IllegalArgumentException("Invalid MENTOR_RATING_UPDATED event");
            }
            log.info("Kafka event consumed: type=MENTOR_RATING_UPDATED, mentorId={}", mentorId);
            ratingService.applyRating(mentorId, average, reviewCount);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}