package com.edumentor.mentor.controller;

import com.edumentor.mentor.dto.AvailabilityResponse;
import com.edumentor.mentor.dto.MentorProfileRequest;
import com.edumentor.mentor.dto.MentorResponse;
import com.edumentor.mentor.dto.PageResponse;
import com.edumentor.mentor.dto.PublicMentorResponse;
import com.edumentor.mentor.dto.SubmitVerificationRequest;
import com.edumentor.mentor.dto.UpdateAvailabilityRequest;
import com.edumentor.mentor.dto.VerificationResponse;
import com.edumentor.mentor.entity.Exam;
import com.edumentor.mentor.security.AuthenticatedUser;
import com.edumentor.mentor.service.MentorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mentors")
@RequiredArgsConstructor
@Tag(name = "Mentors")
@SecurityRequirement(name = "bearerAuth")
public class MentorController {

    private final MentorService mentorService;

    // ----- MENTOR: own profile -----

    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Create my mentor profile (starts as PENDING)")
    public MentorResponse createProfile(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestBody MentorProfileRequest request) {
        return mentorService.createProfile(user.userId(), request);
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Update my profile (changing credentials triggers re-verification)")
    public MentorResponse updateProfile(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestBody MentorProfileRequest request) {
        return mentorService.updateProfile(user.userId(), request);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Get my profile and verification status")
    public MentorResponse getOwnProfile(@AuthenticationPrincipal AuthenticatedUser user) {
        return mentorService.getOwnProfile(user.userId());
    }

    @PostMapping("/me/verification")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Submit verification proof metadata")
    public VerificationResponse submitVerification(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @Valid @RequestBody SubmitVerificationRequest request) {
        return mentorService.submitVerification(user.userId(), request);
    }

    @GetMapping("/me/verification")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "List my verification submissions")
    public List<VerificationResponse> listOwnVerifications(@AuthenticationPrincipal AuthenticatedUser user) {
        return mentorService.listOwnVerifications(user.userId());
    }

    @PutMapping("/me/availability")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Replace my weekly availability (APPROVED mentors only)")
    public List<AvailabilityResponse> replaceAvailability(@AuthenticationPrincipal AuthenticatedUser user,
                                                          @Valid @RequestBody UpdateAvailabilityRequest request) {
        return mentorService.replaceAvailability(user.userId(), request);
    }

    // ----- Any authenticated user: approved mentors only -----

    @GetMapping
    @Operation(summary = "Search approved mentors")
    public PageResponse<PublicMentorResponse> search(@RequestParam(required = false) Exam exam,
                                                     @RequestParam(required = false) String college,
                                                     @RequestParam(required = false) String branch,
                                                     @RequestParam(required = false) String location,
                                                     @RequestParam(required = false) Double minRating,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return mentorService.search(exam, college, branch, location, minRating, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an approved mentor")
    public PublicMentorResponse getMentor(@PathVariable Long id) {
        return mentorService.getApprovedMentor(id);
    }

    @GetMapping("/{id}/availability")
    @Operation(summary = "Get an approved mentor's weekly availability")
    public List<AvailabilityResponse> getAvailability(@PathVariable Long id) {
        return mentorService.getAvailability(id);
    }
}