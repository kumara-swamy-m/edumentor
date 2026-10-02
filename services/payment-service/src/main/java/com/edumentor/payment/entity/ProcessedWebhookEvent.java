package com.edumentor.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "processed_webhook_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_webhook_provider_event",
                columnNames = {"provider", "provider_event_id"}))
@Getter
@NoArgsConstructor
public class ProcessedWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_event_id", nullable = false, length = 100)
    private String providerEventId;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedWebhookEvent(String provider, String providerEventId, String outcome, Instant processedAt) {
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.outcome = outcome;
        this.processedAt = processedAt;
    }
}