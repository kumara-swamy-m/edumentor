package com.edumentor.notification.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Dev provider: logs the email instead of sending it, so no SMTP credentials are needed. */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "mock", matchIfMissing = true)
public class MockEmailProvider implements EmailProvider {

    @Override
    public void send(EmailMessage message) {
        log.info("[MOCK EMAIL] to={} subject=\"{}\"\n{}", message.to(), message.subject(), message.body());
    }
}