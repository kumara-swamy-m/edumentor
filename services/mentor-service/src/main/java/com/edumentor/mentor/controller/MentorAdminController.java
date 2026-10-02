package com.edumentor.mentor.controller;

import com.edumentor.mentor.dto.AdminMentorResponse;
import com.edumentor.mentor.dto.MentorResponse;
import com.edumentor.mentor.dto.PageResponse;
import com.edumentor.mentor.dto.RejectMentorRequest;
import com.edumentor.mentor.entity.VerificationStatus;
import com.edumentor.mentor.security.AuthenticatedUser;
import com.edumentor.mentor.service.MentorAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mentors/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Mentor administration")
@SecurityRequirement(name = "bearerAuth")
public class MentorAdminController {

    private final MentorAdminService adminService;

    @GetMapping("/applications")
    @Operation(summary = "List mentor applications by status (default PENDING, oldest first)")
    public PageResponse<MentorResponse> listApplications(
            @RequestParam(defaultValue = "PENDING") VerificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.listApplications(status, page, size);
    }

    @GetMapping("/applications/{id}")
    @Operation(summary = "Application detail with proof submissions and applicant email")
    public AdminMentorResponse getApplication(@PathVariable Long id,
                                              @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return adminService.getApplication(id, authorization);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a PENDING mentor (requires submitted proof)")
    public MentorResponse approve(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser admin) {
        return adminService.approve(id, admin.userId());
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a PENDING mentor with a comment")
    public MentorResponse reject(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser admin,
                                 @Valid @RequestBody RejectMentorRequest request) {
        return adminService.reject(id, admin.userId(), request.comment());
    }
}