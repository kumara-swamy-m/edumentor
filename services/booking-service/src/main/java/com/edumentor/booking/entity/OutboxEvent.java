package com.edumentor.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Written in the same transaction as the state change; the relay publishes PENDING rows to Kafka. */
@Entity
@Table(name = "outbox_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_outbox_event_id", columnNames = "event_id"),
        indexes = @Index(name = "idx_outbox_status_id", columnList = "status,id"))
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 30)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 50)
    private String aggregateId;

    @Column(name = "message_key", length = 50)
    private String messageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private OutboxEventType eventType;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;
}