package com.edumentor.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        @DefaultValue("mock") String provider,
        @DefaultValue("512") int dimensions,
        @DefaultValue("pgvector") String vectorStore,
        @DefaultValue("10") int recommendationsPerMinute
) {
}