package com.edumentor.review.controller;

import com.edumentor.review.dto.CreateReviewRequest;
import com.edumentor.review.dto.PageResponse;
import com.edumentor.review.dto.PublicReviewResponse;
import com.edumentor.review.dto.RatingSummaryResponse;
import com.edumentor.review.dto.ReviewResponse;
import com.edumentor.review.security.AuthenticatedUser;
import com.edumentor.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews")
@SecurityRequirement(name = "bearerAuth")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Review a completed session (one review per booking, rating 1-5)")
    public ReviewResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                 @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                 @Valid @RequestBody CreateReviewRequest request) {
        return reviewService.createReview(user, authorization, request);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "My reviews, newest first")
    public PageResponse<ReviewResponse> mine(@AuthenticationPrincipal AuthenticatedUser user,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return reviewService.listMine(user.userId(), page, size);
    }

    @GetMapping("/mentor/{mentorId}")
    @Operation(summary = "Reviews of a mentor, newest first (reviewer identity is not exposed)")
    public PageResponse<PublicReviewResponse> forMentor(@PathVariable Long mentorId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return reviewService.listForMentor(mentorId, page, size);
    }

    @GetMapping("/mentor/{mentorId}/summary")
    @Operation(summary = "Average rating and review count of a mentor")
    public RatingSummaryResponse summary(@PathVariable Long mentorId) {
        return reviewService.summary(mentorId);
    }
}