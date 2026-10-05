package com.edumentor.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "mentor-service", url = "${app.clients.mentor-service.url}")
public interface MentorClient {

    @GetMapping("/api/mentors/{id}")
    PublicMentor getMentor(@PathVariable("id") Long id,
                           @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                           @RequestHeader("X-Correlation-ID") String correlationId);

    @GetMapping("/api/mentors")
    PageDto<PublicMentor> search(@RequestParam("page") int page,
                                 @RequestParam("size") int size,
                                 @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                 @RequestHeader("X-Correlation-ID") String correlationId);
}