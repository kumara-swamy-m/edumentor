package com.edumentor.mentor.dto;

import java.util.List;

/** Application detail for admins. {@code applicantEmail} is null when auth-service is unavailable. */
public record AdminMentorResponse(MentorResponse mentor, String applicantEmail,
                                  List<VerificationResponse> verifications) {
}