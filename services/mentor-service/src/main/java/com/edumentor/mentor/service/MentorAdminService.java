package com.edumentor.mentor.service;

import com.edumentor.mentor.client.AuthClient;
import com.edumentor.mentor.client.UserSummary;
import com.edumentor.mentor.dto.AdminMentorResponse;
import com.edumentor.mentor.dto.MentorResponse;
import com.edumentor.mentor.dto.PageResponse;
import com.edumentor.mentor.dto.VerificationResponse;
import com.edumentor.mentor.entity.MentorProfile;
import com.edumentor.mentor.entity.MentorVerification;
import com.edumentor.mentor.entity.VerificationStatus;
import com.edumentor.mentor.exception.ApiException;
import com.edumentor.mentor.repository.MentorProfileRepository;
import com.edumentor.mentor.repository.MentorVerificationRepository;
import com.edumentor.mentor.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MentorAdminService {

    private static final int MAX_PAGE_SIZE = 50;

    private final MentorProfileRepository profileRepository;
    private final MentorVerificationRepository verificationRepository;
    private final AuthClient authClient;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public PageResponse<MentorResponse> listApplications(VerificationStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, "createdAt"));
        return PageResponse.from(profileRepository.findByVerificationStatus(status, pageable),
                MentorMapper::toMentorResponse);
    }

    public AdminMentorResponse getApplication(Long mentorId, String authorization) {
        AdminMentorResponse loaded = Objects.requireNonNull(
                transactionTemplate.execute(status -> loadApplication(mentorId)));
        String email = lookupApplicantEmail(loaded.mentor().userId(), authorization);
        return new AdminMentorResponse(loaded.mentor(), email, loaded.verifications());
    }

    @Transactional
    public MentorResponse approve(Long mentorId, Long adminUserId) {
        MentorProfile profile = requireProfile(mentorId);
        requirePending(profile);
        MentorVerification proof = verificationRepository
                .findFirstByMentorIdAndStatusOrderBySubmittedAtDescIdDesc(mentorId, VerificationStatus.PENDING)
                .orElseThrow(ApiException::verificationProofMissing);

        markReviewed(proof, VerificationStatus.APPROVED, adminUserId);
        profile.setVerificationStatus(VerificationStatus.APPROVED);
        profile.setVerificationNote(null);
        log.info("Mentor approved: mentorId={}, adminId={}", mentorId, adminUserId);
        return MentorMapper.toMentorResponse(profile);
    }

    @Transactional
    public MentorResponse reject(Long mentorId, Long adminUserId, String comment) {
        MentorProfile profile = requireProfile(mentorId);
        requirePending(profile);

        verificationRepository
                .findFirstByMentorIdAndStatusOrderBySubmittedAtDescIdDesc(mentorId, VerificationStatus.PENDING)
                .ifPresent(proof -> markReviewed(proof, VerificationStatus.REJECTED, adminUserId));
        profile.setVerificationStatus(VerificationStatus.REJECTED);
        profile.setVerificationNote(comment.trim());
        log.info("Mentor rejected: mentorId={}, adminId={}", mentorId, adminUserId);
        return MentorMapper.toMentorResponse(profile);
    }

    // ---------- helpers ----------

    private AdminMentorResponse loadApplication(Long mentorId) {
        MentorProfile profile = requireProfile(mentorId);
        List<VerificationResponse> verifications = verificationRepository
                .findByMentorIdOrderBySubmittedAtDescIdDesc(mentorId).stream()
                .map(MentorMapper::toVerificationResponse)
                .toList();
        return new AdminMentorResponse(MentorMapper.toMentorResponse(profile), null, verifications);
    }

    private String lookupApplicantEmail(Long userId, String authorization) {
        try {
            UserSummary user = authClient.getUser(userId, authorization, MDC.get(CorrelationIdFilter.MDC_KEY));
            return user == null ? null : user.email();
        } catch (RuntimeException ex) {
            // The fallback normally absorbs failures; this keeps the admin page usable regardless.
            log.warn("Applicant lookup failed for userId={}: {}", userId, ex.getClass().getSimpleName());
            return null;
        }
    }

    private MentorProfile requireProfile(Long mentorId) {
        return profileRepository.findById(mentorId).orElseThrow(ApiException::mentorNotFound);
    }

    private void requirePending(MentorProfile profile) {
        if (profile.getVerificationStatus() != VerificationStatus.PENDING) {
            throw ApiException.invalidVerificationState(
                    "Only PENDING applications can be reviewed; current status is "
                            + profile.getVerificationStatus());
        }
    }

    private void markReviewed(MentorVerification verification, VerificationStatus status, Long adminUserId) {
        verification.setStatus(status);
        verification.setReviewedBy(adminUserId);
        verification.setReviewedAt(LocalDateTime.now());
    }
}