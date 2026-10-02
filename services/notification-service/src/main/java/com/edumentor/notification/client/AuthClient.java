package com.edumentor.notification.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "auth-service", url = "${app.clients.auth-service.url}")
public interface AuthClient {

    @GetMapping("/api/auth/internal/users/{id}")
    UserInfo getUser(@PathVariable("id") Long id, @RequestHeader("X-Internal-Api-Key") String apiKey);
}