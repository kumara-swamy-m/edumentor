package com.edumentor.booking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "mentor-service", url = "${app.clients.mentor-service.url}",
        fallbackFactory = MentorClientFallbackFactory.class)
public interface MentorClient {

    @GetMapping("/api/mentors/me")
    MentorOwnProfile getOwnProfile(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                   @RequestHeader("X-Correlation-ID") String correlationId);

    @GetMapping("/api/mentors/{id}")
    MentorPublicProfile getApprovedMentor(@PathVariable("id") Long id,
                                          @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                          @RequestHeader("X-Correlation-ID") String correlationId);
}