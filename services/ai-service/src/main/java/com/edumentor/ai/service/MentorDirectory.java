package com.edumentor.ai.service;

import com.edumentor.ai.client.MentorClient;
import com.edumentor.ai.client.PageDto;
import com.edumentor.ai.client.PublicMentor;
import com.edumentor.ai.exception.ApiException;
import com.edumentor.ai.web.CorrelationIdFilter;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MentorDirectory {

    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES = 100;

    private final MentorClient client;

    /** Empty when mentor-service says the mentor does not exist or is not approved. */
    public Optional<PublicMentor> find(Long id, String authorization) {
        try {
            return Optional.ofNullable(client.getMentor(id, authorization, correlationId()));
        } catch (FeignException.NotFound ex) {
            return Optional.empty();
        } catch (FeignException.Forbidden | FeignException.Unauthorized ex) {
            throw ApiException.mentorAccessDenied();
        } catch (RuntimeException ex) {
            log.warn("mentor-service lookup failed: {}", ex.getClass().getSimpleName());
            throw ApiException.mentorServiceUnavailable();
        }
    }

    /** All currently approved mentors (mentor-service only ever returns approved ones to this endpoint). */
    public List<PublicMentor> listApproved(String authorization) {
        List<PublicMentor> all = new ArrayList<>();
        try {
            for (int page = 0; page < MAX_PAGES; page++) {
                PageDto<PublicMentor> result = client.search(page, PAGE_SIZE, authorization, correlationId());
                if (result == null) {
                    throw ApiException.mentorServiceUnavailable();
                }
                all.addAll(result.content());
                if (page + 1 >= result.totalPages()) {
                    break;
                }
            }
        } catch (FeignException.Forbidden | FeignException.Unauthorized ex) {
            throw ApiException.mentorAccessDenied();
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("mentor-service listing failed: {}", ex.getClass().getSimpleName());
            throw ApiException.mentorServiceUnavailable();
        }
        return all;
    }

    private String correlationId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}