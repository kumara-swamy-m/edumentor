package com.edumentor.payment.controller;

import com.edumentor.payment.dto.WebhookResponse;
import com.edumentor.payment.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/payments/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping("/{provider}")
    @Operation(summary = "Provider webhook (no JWT; authenticated by signature)")
    public WebhookResponse receive(@PathVariable String provider, @RequestBody byte[] body,
                                   @RequestHeader HttpHeaders headers) {
        WebhookService.Result result = webhookService.handle(provider, new String(body, StandardCharsets.UTF_8),
                headers);
        return new WebhookResponse(result.name().toLowerCase());
    }
}