package com.edumentor.mentor.service;

import com.edumentor.mentor.client.AuthClient;
import com.edumentor.mentor.entity.MentorProfile;
import com.edumentor.mentor.entity.MentorVerification;
import com.edumentor.mentor.entity.VerificationStatus;
import com.edumentor.mentor.exception.ApiException;
import com.edumentor.mentor.repository.MentorProfileRepository;
import com.edumentor.mentor.repository.MentorVerificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorAdminServiceTest {

    @Mock
    private MentorProfileRepository profileRepository;
    @Mock
    private MentorVerificationRepository verificationRepository;
    @Mock
    private AuthClient authClient;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private MentorAdminService service;

    @Test
    void approveMovesProfileAndProofToApproved() {
        MentorProfile profile = profile(VerificationStatus.PENDING);
        MentorVerification proof = proof();
        when(profileRepository.findById(5L)).thenReturn(Optional.of(profile));
        when(verificationRepository.findFirstByMentorIdAndStatusOrderBySubmittedAtDescIdDesc(
                5L, VerificationStatus.PENDING)).thenReturn(Optional.of(proof));

        service.approve(5L, 1L);

        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(proof.getStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(proof.getReviewedBy()).isEqualTo(1L);
        assertThat(proof.getReviewedAt()).isNotNull();
    }

    @Test
    void approveFailsWithoutSubmittedProof() {
        MentorProfile profile = profile(VerificationStatus.PENDING);
        when(profileRepository.findById(5L)).thenReturn(Optional.of(profile));
        when(verificationRepository.findFirstByMentorIdAndStatusOrderBySubmittedAtDescIdDesc(
                5L, VerificationStatus.PENDING)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(5L, 1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "VERIFICATION_PROOF_MISSING");
        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
    }

    @Test
    void approveFailsWhenNotPending() {
        when(profileRepository.findById(5L)).thenReturn(Optional.of(profile(VerificationStatus.APPROVED)));

        assertThatThrownBy(() -> service.approve(5L, 1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_VERIFICATION_STATE");
        verifyNoInteractions(verificationRepository);
    }

    @Test
    void rejectStoresCommentEvenWithoutProof() {
        MentorProfile profile = profile(VerificationStatus.PENDING);
        when(profileRepository.findById(5L)).thenReturn(Optional.of(profile));
        when(verificationRepository.findFirstByMentorIdAndStatusOrderBySubmittedAtDescIdDesc(
                5L, VerificationStatus.PENDING)).thenReturn(Optional.empty());

        service.reject(5L, 1L, "  ID card is unreadable ");

        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(profile.getVerificationNote()).isEqualTo("ID card is unreadable");
    }

    @Test
    void rejectMarksPendingProofAsRejected() {
        MentorProfile profile = profile(VerificationStatus.PENDING);
        MentorVerification proof = proof();
        when(profileRepository.findById(5L)).thenReturn(Optional.of(profile));
        when(verificationRepository.findFirstByMentorIdAndStatusOrderBySubmittedAtDescIdDesc(
                5L, VerificationStatus.PENDING)).thenReturn(Optional.of(proof));

        service.reject(5L, 1L, "Mismatch");

        assertThat(proof.getStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(proof.getReviewedBy()).isEqualTo(1L);
    }

    @Test
    void unknownMentorIsNotFound() {
        when(profileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(99L, 1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "MENTOR_NOT_FOUND");
    }

    private MentorProfile profile(VerificationStatus status) {
        MentorProfile p = new MentorProfile();
        p.setId(5L);
        p.setUserId(100L);
        p.setVerificationStatus(status);
        return p;
    }

    private MentorVerification proof() {
        MentorVerification v = new MentorVerification();
        v.setStatus(VerificationStatus.PENDING);
        return v;
    }
}