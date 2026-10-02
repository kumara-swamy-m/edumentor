package com.edumentor.mentor.service;

import com.edumentor.mentor.dto.AvailabilityRequest;
import com.edumentor.mentor.dto.AvailabilityResponse;
import com.edumentor.mentor.dto.MentorProfileRequest;
import com.edumentor.mentor.dto.MentorResponse;
import com.edumentor.mentor.dto.PageResponse;
import com.edumentor.mentor.dto.PublicMentorResponse;
import com.edumentor.mentor.dto.SubmitVerificationRequest;
import com.edumentor.mentor.dto.UpdateAvailabilityRequest;
import com.edumentor.mentor.dto.VerificationResponse;
import com.edumentor.mentor.entity.Exam;
import com.edumentor.mentor.entity.MentorAvailability;
import com.edumentor.mentor.entity.MentorProfile;
import com.edumentor.mentor.entity.MentorVerification;
import com.edumentor.mentor.entity.VerificationStatus;
import com.edumentor.mentor.exception.ApiException;
import com.edumentor.mentor.repository.MentorProfileRepository;
import com.edumentor.mentor.repository.MentorSpecifications;
import com.edumentor.mentor.repository.MentorVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MentorService {

    private static final int MAX_PAGE_SIZE = 50;

    private final MentorProfileRepository profileRepository;
    private final MentorVerificationRepository verificationRepository;

    // ---------- Mentor: own profile ----------

    @Transactional
    public MentorResponse createProfile(Long userId, MentorProfileRequest request) {
        if (profileRepository.existsByUserId(userId)) {
            throw ApiException.profileAlreadyExists();
        }
        MentorProfile profile = new MentorProfile();
        profile.setUserId(userId);
        profile.setVerificationStatus(VerificationStatus.PENDING);
        apply(profile, request);
        try {
            profile = profileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.profileAlreadyExists();
        }
        log.info("Mentor profile created: mentorId={}, userId={}", profile.getId(), userId);
        return MentorMapper.toMentorResponse(profile);
    }

    @Transactional
    public MentorResponse updateProfile(Long userId, MentorProfileRequest request) {
        MentorProfile profile = requireOwnProfile(userId);
        boolean credentialsChanged = credentialsChanged(profile, request);
        apply(profile, request);
        if (credentialsChanged && profile.getVerificationStatus() != VerificationStatus.PENDING) {
            profile.setVerificationStatus(VerificationStatus.PENDING);
            profile.setVerificationNote(null);
            log.info("Mentor credentials changed, re-verification required: mentorId={}", profile.getId());
        }
        return MentorMapper.toMentorResponse(profile);
    }

    @Transactional(readOnly = true)
    public MentorResponse getOwnProfile(Long userId) {
        return MentorMapper.toMentorResponse(requireOwnProfile(userId));
    }

    // ---------- Mentor: verification proof ----------

    @Transactional
    public VerificationResponse submitVerification(Long userId, SubmitVerificationRequest request) {
        MentorProfile profile = requireOwnProfile(userId);
        if (profile.getVerificationStatus() == VerificationStatus.APPROVED) {
            throw ApiException.invalidVerificationState("Mentor is already approved");
        }
        MentorVerification verification = new MentorVerification();
        verification.setMentor(profile);
        verification.setDocumentType(request.documentType());
        verification.setDocumentReference(request.documentReference().trim());
        verification.setNote(request.note() == null ? null : request.note().trim());
        verification.setStatus(VerificationStatus.PENDING);
        verification = verificationRepository.save(verification);

        if (profile.getVerificationStatus() == VerificationStatus.REJECTED) {
            profile.setVerificationStatus(VerificationStatus.PENDING);
            profile.setVerificationNote(null);
        }
        log.info("Mentor verification submitted: mentorId={}, documentType={}",
                profile.getId(), verification.getDocumentType());
        return MentorMapper.toVerificationResponse(verification);
    }

    @Transactional(readOnly = true)
    public List<VerificationResponse> listOwnVerifications(Long userId) {
        MentorProfile profile = requireOwnProfile(userId);
        return verificationRepository.findByMentorIdOrderBySubmittedAtDescIdDesc(profile.getId()).stream()
                .map(MentorMapper::toVerificationResponse)
                .toList();
    }

    // ---------- Mentor: availability ----------

    @Transactional
    public List<AvailabilityResponse> replaceAvailability(Long userId, UpdateAvailabilityRequest request) {
        MentorProfile profile = requireOwnProfile(userId);
        if (profile.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw ApiException.mentorNotApproved();
        }
        validateWindows(request.windows());

        profile.getAvailability().clear();
        for (AvailabilityRequest window : request.windows()) {
            profile.getAvailability().add(new MentorAvailability(
                    profile, window.dayOfWeek(), window.startTime(), window.endTime()));
        }
        log.info("Mentor availability updated: mentorId={}, windows={}", profile.getId(), request.windows().size());
        return MentorMapper.toAvailabilityResponses(profile.getAvailability());
    }

    // ---------- Students: approved mentors only ----------

    @Transactional(readOnly = true)
    public PageResponse<PublicMentorResponse> search(Exam exam, String college, String branch, String location,
                                                     Double minRating, int page, int size) {
        Specification<MentorProfile> spec = Specification.where(MentorSpecifications.approved())
                .and(MentorSpecifications.hasExam(exam))
                .and(MentorSpecifications.collegeContains(college))
                .and(MentorSpecifications.branchContains(branch))
                .and(MentorSpecifications.locationContains(location))
                .and(MentorSpecifications.ratingAtLeast(minRating));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(profileRepository.findAll(spec, pageable), MentorMapper::toPublicResponse);
    }

    @Transactional(readOnly = true)
    public PublicMentorResponse getApprovedMentor(Long mentorId) {
        return MentorMapper.toPublicResponse(requireApproved(mentorId));
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> getAvailability(Long mentorId) {
        return MentorMapper.toAvailabilityResponses(requireApproved(mentorId).getAvailability());
    }

    // ---------- helpers ----------

    private MentorProfile requireOwnProfile(Long userId) {
        return profileRepository.findByUserId(userId).orElseThrow(ApiException::profileNotFound);
    }

    private MentorProfile requireApproved(Long mentorId) {
        return profileRepository.findByIdAndVerificationStatus(mentorId, VerificationStatus.APPROVED)
                .orElseThrow(ApiException::mentorNotFound);
    }

    private void apply(MentorProfile profile, MentorProfileRequest request) {
        profile.setName(request.name().trim());
        profile.setCollege(request.college().trim());
        profile.setCourse(request.course().trim());
        profile.setBranch(request.branch().trim());
        profile.setYear(request.year());
        profile.setExamPath(request.examPath());
        profile.setRank(request.rank());
        profile.setBio(request.bio() == null ? null : request.bio().trim());
        profile.setLocation(request.location().trim());
        profile.replaceExpertise(request.expertise());
    }

    private boolean credentialsChanged(MentorProfile profile, MentorProfileRequest request) {
        return !profile.getCollege().equals(request.college().trim())
                || !profile.getCourse().equals(request.course().trim())
                || !profile.getBranch().equals(request.branch().trim())
                || !profile.getYear().equals(request.year())
                || profile.getExamPath() != request.examPath()
                || !Objects.equals(profile.getRank(), request.rank());
    }

    private void validateWindows(List<AvailabilityRequest> windows) {
        Map<DayOfWeek, List<AvailabilityRequest>> byDay = new EnumMap<>(DayOfWeek.class);
        for (AvailabilityRequest window : windows) {
            if (!window.endTime().isAfter(window.startTime())) {
                throw ApiException.invalidAvailability(
                        "End time must be after start time for " + window.dayOfWeek());
            }
            byDay.computeIfAbsent(window.dayOfWeek(), d -> new ArrayList<>()).add(window);
        }
        for (Map.Entry<DayOfWeek, List<AvailabilityRequest>> entry : byDay.entrySet()) {
            List<AvailabilityRequest> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(Comparator.comparing(AvailabilityRequest::startTime));
            for (int i = 1; i < sorted.size(); i++) {
                if (sorted.get(i).startTime().isBefore(sorted.get(i - 1).endTime())) {
                    throw ApiException.invalidAvailability("Windows overlap on " + entry.getKey());
                }
            }
        }
    }
}