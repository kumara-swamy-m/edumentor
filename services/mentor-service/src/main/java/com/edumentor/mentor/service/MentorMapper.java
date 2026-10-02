package com.edumentor.mentor.service;

import com.edumentor.mentor.dto.AvailabilityResponse;
import com.edumentor.mentor.dto.MentorResponse;
import com.edumentor.mentor.dto.PublicMentorResponse;
import com.edumentor.mentor.dto.VerificationResponse;
import com.edumentor.mentor.entity.MentorAvailability;
import com.edumentor.mentor.entity.MentorExpertise;
import com.edumentor.mentor.entity.MentorProfile;
import com.edumentor.mentor.entity.MentorVerification;

import java.util.Comparator;
import java.util.List;

public final class MentorMapper {

    private MentorMapper() {
    }

    public static MentorResponse toMentorResponse(MentorProfile p) {
        return new MentorResponse(p.getId(), p.getUserId(), p.getName(), p.getCollege(), p.getCourse(),
                p.getBranch(), p.getYear(), p.getExamPath(), p.getRank(), p.getBio(), p.getLocation(),
                p.getVerificationStatus(), p.getVerificationNote(), p.getRating(), p.getReviewCount(),
                topics(p), p.getCreatedAt(), p.getUpdatedAt());
    }

    public static PublicMentorResponse toPublicResponse(MentorProfile p) {
        return new PublicMentorResponse(p.getId(), p.getName(), p.getCollege(), p.getCourse(), p.getBranch(),
                p.getYear(), p.getExamPath(), p.getRank(), p.getBio(), p.getLocation(), p.getRating(),
                p.getReviewCount(), topics(p));
    }

    public static VerificationResponse toVerificationResponse(MentorVerification v) {
        return new VerificationResponse(v.getId(), v.getDocumentType(), v.getDocumentReference(), v.getNote(),
                v.getStatus(), v.getSubmittedAt(), v.getReviewedBy(), v.getReviewedAt());
    }

    public static List<AvailabilityResponse> toAvailabilityResponses(List<MentorAvailability> windows) {
        return windows.stream()
                .sorted(Comparator.comparing(MentorAvailability::getDayOfWeek)
                        .thenComparing(MentorAvailability::getStartTime))
                .map(a -> new AvailabilityResponse(a.getDayOfWeek(), a.getStartTime(), a.getEndTime()))
                .toList();
    }

    private static List<String> topics(MentorProfile p) {
        return p.getExpertise().stream().map(MentorExpertise::getTopic).toList();
    }
}