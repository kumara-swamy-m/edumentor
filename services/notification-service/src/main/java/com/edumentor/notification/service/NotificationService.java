package com.edumentor.notification.service;

import com.edumentor.notification.client.UserInfo;
import com.edumentor.notification.dto.NotificationResponse;
import com.edumentor.notification.dto.PageResponse;
import com.edumentor.notification.email.EmailMessage;
import com.edumentor.notification.email.EmailProvider;
import com.edumentor.notification.entity.Notification;
import com.edumentor.notification.repository.NotificationRepository;
import com.edumentor.notification.web.CorrelationIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final NotificationComposer composer;
    private final UserDirectory userDirectory;
    private final EmailProvider emailProvider;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Handles one Kafka event. Idempotent per (event id, recipient). A user lookup failure is rethrown so
     * Kafka retries; an email delivery failure is recorded as FAILED and not retried.
     */
    public void handleEvent(String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Malformed event", ex);
        }
        String correlationId = root.path("correlationId").asText(null);
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        }
        try {
            String eventType = root.path("eventType").asText("");
            List<NotificationComposer.Draft> drafts = composer.compose(eventType, root);
            if (drafts.isEmpty()) {
                log.debug("Event needs no notification: type={}", eventType);
                return;
            }
            String eventId = root.path("eventId").asText(null);
            if (eventId == null || eventId.isBlank()) {
                throw new IllegalArgumentException("Event without eventId: " + eventType);
            }
            log.info("Kafka event consumed: type={}, eventId={}", eventType, eventId);
            drafts.forEach(draft -> deliver(eventId, draft));
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private void deliver(String eventId, NotificationComposer.Draft draft) {
        if (notificationRepository.existsBySourceEventIdAndUserId(eventId, draft.userId())) {
            log.info("Notification already handled: eventId={}, userId={}", eventId, draft.userId());
            return;
        }
        UserInfo user = userDirectory.require(draft.userId());

        Notification notification = new Notification();
        notification.setUserId(draft.userId());
        notification.setRecipientEmail(user.email());
        notification.setType(draft.type());
        notification.setSubject(draft.subject());
        notification.setBody(draft.body().replace("{name}", user.name() == null ? "there" : user.name()));
        notification.setSourceEventId(eventId);
        try {
            notification = notificationRepository.saveAndFlush(notification);
        } catch (DataIntegrityViolationException ex) {
            log.info("Notification already handled (concurrent delivery): eventId={}", eventId);
            return;
        }

        try {
            emailProvider.send(new EmailMessage(user.email(), notification.getSubject(), notification.getBody()));
            notification.markSent(clock.instant());
        } catch (RuntimeException ex) {
            log.warn("Email delivery failed: notificationId={}, cause={}", notification.getId(),
                    ex.getClass().getSimpleName());
            notification.markFailed(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listMine(Long userId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(notificationRepository.findByUserId(userId, pageable), NotificationResponse::from);
    }
}