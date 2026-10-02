package com.edumentor.booking.service;

import com.edumentor.booking.client.MentorClient;
import com.edumentor.booking.client.MentorOwnProfile;
import com.edumentor.booking.client.MentorPublicProfile;
import com.edumentor.booking.exception.ApiException;
import com.edumentor.booking.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Thin domain wrapper around the mentor-service client. */
@Component
@RequiredArgsConstructor
public class MentorDirectory {

    public record MentorIdentity(Long mentorId, Long userId) {
    }

    private final MentorClient mentorClient;

    /** The caller's own mentor profile must exist, belong to them and be APPROVED. */
    public MentorIdentity requireApprovedOwnProfile(String authorization, Long expectedUserId) {
        MentorOwnProfile profile = mentorClient.getOwnProfile(authorization, correlationId());
        if (profile == null) {
            throw ApiException.mentorServiceUnavailable();
        }
        if (!"APPROVED".equals(profile.verificationStatus()) || !expectedUserId.equals(profile.userId())) {
            throw ApiException.mentorNotApproved();
        }
        return new MentorIdentity(profile.id(), profile.userId());
    }

    /** mentor-service answers 404 for mentors that are not approved. */
    public void requireApprovedMentor(Long mentorId, String authorization) {
        MentorPublicProfile profile = mentorClient.getApprovedMentor(mentorId, authorization, correlationId());
        if (profile == null) {
            throw ApiException.mentorServiceUnavailable();
        }
    }

    private String correlationId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}