package com.edumentor.ai.controller;

import com.edumentor.ai.dto.RecommendationRequest;
import com.edumentor.ai.dto.RecommendationResponse;
import com.edumentor.ai.security.AuthenticatedUser;
import com.edumentor.ai.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "Recommendations")
@SecurityRequirement(name = "bearerAuth")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @PostMapping("/recommendations")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    @Operation(summary = "Recommend verified mentors for a student",
            description = "Semantic search over approved mentors of the requested exam. matchScore is cosine "
                    + "similarity (0-1), a relevance score and not a probability. Reasons must be rendered as "
                    + "plain text.")
    public RecommendationResponse recommend(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Valid @RequestBody RecommendationRequest request) {
        return recommendationService.recommend(user.userId(), request);
    }
}