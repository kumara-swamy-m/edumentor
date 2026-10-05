package com.edumentor.review.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "booking-service", url = "${app.clients.booking-service.url}",
        fallbackFactory = BookingClientFallbackFactory.class)
public interface BookingClient {

    @GetMapping("/api/bookings/{id}")
    BookingInfo getBooking(@PathVariable("id") Long id,
                           @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                           @RequestHeader("X-Correlation-ID") String correlationId);
}