package com.edumentor.payment.repository;

import com.edumentor.payment.entity.ProcessedWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEvent, Long> {

    boolean existsByProviderAndProviderEventId(String provider, String providerEventId);
}