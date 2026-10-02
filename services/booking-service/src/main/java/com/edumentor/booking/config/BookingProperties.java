package com.edumentor.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(
        @DefaultValue("PT10M") Duration holdDuration,
        @DefaultValue("3") int maxPendingPerStudent,
        @DefaultValue("PT30M") Duration minSlotLeadTime,
        @DefaultValue("100") int expiryBatchSize
) {
}