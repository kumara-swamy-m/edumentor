package com.edumentor.mentor.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "auth-service", url = "${app.clients.auth-service.url}",
        fallbackFactory = AuthClientFallbackFactory.class)
public interface AuthClient {

    @GetMapping("/api/auth/admin/users/{id}")
    UserSummary getUser(@PathVariable("id") Long id,
                        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                        @RequestHeader("X-Correlation-ID") String correlationId);
}