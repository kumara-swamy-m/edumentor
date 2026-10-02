package com.edumentor.mentor.service;

import com.edumentor.mentor.dto.AvailabilityRequest;
import com.edumentor.mentor.dto.AvailabilityResponse;
import com.edumentor.mentor.dto.MentorProfileRequest;
import com.edumentor.mentor.dto.MentorResponse;
import com.edumentor.mentor.dto.UpdateAvailabilityRequest;
import com.edumentor.mentor.entity.Exam;
import com.edumentor.mentor.entity.MentorExpertise;
import com.edumentor.mentor.entity.MentorProfile;
import com.edumentor.mentor.entity.VerificationStatus;
import com.edumentor.mentor.exception.ApiException;
import com.edumentor.mentor.repository.MentorProfileRepository;
import com.edumentor.mentor.repository.MentorVerificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorServiceTest {

    @Mock
    private MentorProfileRepository profileRepository;
    @Mock
    private MentorVerificationRepository verificationRepository;

    @InjectMocks
    private MentorService service;

    @Test
    void createProfileRejectsSecondProfileForSameUser() {
        when(profileRepository.existsByUserId(100L)).thenReturn(true);

        assertThatThrownBy(() -> service.createProfile(100L, request("RVCE", "bio")))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "MENTOR_PROFILE_ALREADY_EXISTS");
        verify(profileRepository, never()).saveAndFlush(any());
    }

    @Test
    void createProfileStartsPendingAndDeduplicatesExpertise() {
        when(profileRepository.saveAndFlush(any(MentorProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        MentorProfileRequest request = new MentorProfileRequest("Rahul", "RVCE", "B.E.", "CSE", 3, Exam.KCET,
                450, "bio", "Bengaluru",
                List.of("KCET counselling", "kcet counselling ", " Branch selection"));

        MentorResponse response = service.createProfile(100L, request);

        assertThat(response.verificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(response.userId()).isEqualTo(100L);
        assertThat(response.expertise()).containsExactly("KCET counselling", "Branch selection");
    }

    @Test
    void changingCredentialsResetsApprovedMentorToPending() {
        MentorProfile profile = approvedProfile();
        when(profileRepository.findByUserId(100L)).thenReturn(Optional.of(profile));

        service.updateProfile(100L, request("PES University", "bio"));

        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(profile.getCollege()).isEqualTo("PES University");
    }

    @Test
    void changingOnlyBioKeepsApproval() {
        MentorProfile profile = approvedProfile();
        when(profileRepository.findByUserId(100L)).thenReturn(Optional.of(profile));

        service.updateProfile(100L, request("RVCE", "A new bio"));

        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(profile.getBio()).isEqualTo("A new bio");
    }

    @Test
    void availabilityRequiresApprovedMentor() {
        MentorProfile profile = approvedProfile();
        profile.setVerificationStatus(VerificationStatus.PENDING);
        when(profileRepository.findByUserId(100L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.replaceAvailability(100L, windows(
                window(DayOfWeek.MONDAY, "09:00", "10:00"))))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "MENTOR_NOT_APPROVED");
    }

    @Test
    void overlappingAvailabilityWindowsAreRejected() {
        when(profileRepository.findByUserId(100L)).thenReturn(Optional.of(approvedProfile()));

        assertThatThrownBy(() -> service.replaceAvailability(100L, windows(
                window(DayOfWeek.MONDAY, "09:00", "12:00"),
                window(DayOfWeek.MONDAY, "11:00", "13:00"))))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_AVAILABILITY");
    }

    @Test
    void validAvailabilityIsStoredAndReturnedSorted() {
        MentorProfile profile = approvedProfile();
        when(profileRepository.findByUserId(100L)).thenReturn(Optional.of(profile));

        List<AvailabilityResponse> result = service.replaceAvailability(100L, windows(
                window(DayOfWeek.WEDNESDAY, "10:00", "11:00"),
                window(DayOfWeek.MONDAY, "09:00", "10:00"),
                window(DayOfWeek.MONDAY, "10:00", "11:00")));

        assertThat(profile.getAvailability()).hasSize(3);
        assertThat(result).extracting(AvailabilityResponse::dayOfWeek)
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
    }

    // ---------- helpers ----------

    private MentorProfile approvedProfile() {
        MentorProfile p = new MentorProfile();
        p.setId(5L);
        p.setUserId(100L);
        p.setName("Rahul");
        p.setCollege("RVCE");
        p.setCourse("B.E.");
        p.setBranch("CSE");
        p.setYear(3);
        p.setExamPath(Exam.KCET);
        p.setRank(450);
        p.setLocation("Bengaluru");
        p.setVerificationStatus(VerificationStatus.APPROVED);
        p.getExpertise().add(new MentorExpertise(p, "KCET counselling"));
        return p;
    }

    private MentorProfileRequest request(String college, String bio) {
        return new MentorProfileRequest("Rahul", college, "B.E.", "CSE", 3, Exam.KCET, 450, bio, "Bengaluru",
                List.of("KCET counselling"));
    }

    private AvailabilityRequest window(DayOfWeek day, String start, String end) {
        return new AvailabilityRequest(day, LocalTime.parse(start), LocalTime.parse(end));
    }

    private UpdateAvailabilityRequest windows(AvailabilityRequest... windows) {
        return new UpdateAvailabilityRequest(List.of(windows));
    }
}