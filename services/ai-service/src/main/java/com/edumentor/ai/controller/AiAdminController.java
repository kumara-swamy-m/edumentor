package com.edumentor.ai.controller;

import com.edumentor.ai.dto.IndexStatusResponse;
import com.edumentor.ai.dto.IndexedMentorResponse;
import com.edumentor.ai.dto.ReindexResponse;
import com.edumentor.ai.service.IndexingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "AI administration")
@SecurityRequirement(name = "bearerAuth")
public class AiAdminController {

    private final IndexingService indexingService;

    @PostMapping("/mentors/{id}/embedding")
    @Operation(summary = "Embed one approved mentor (removes the entry if the mentor is not approved)")
    public IndexedMentorResponse indexMentor(@PathVariable Long id,
                                             @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return indexingService.indexMentor(id, authorization);
    }

    @PostMapping("/admin/reindex")
    @Operation(summary = "Embed all approved mentors and drop entries of mentors that are no longer approved")
    public ReindexResponse reindex(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return indexingService.reindexAll(authorization);
    }

    @GetMapping("/admin/index")
    @Operation(summary = "Active embedding model, dimensions and number of indexed mentors")
    public IndexStatusResponse status() {
        return indexingService.status();
    }
}