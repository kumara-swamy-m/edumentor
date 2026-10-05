package com.edumentor.review.config;

import com.edumentor.review.messaging.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    @Bean
    public NewTopic reviewEventsTopic() {
        return TopicBuilder.name(Topics.REVIEW_EVENTS).partitions(3).replicas(1).build();
    }
}