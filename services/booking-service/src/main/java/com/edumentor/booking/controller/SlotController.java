package com.edumentor.booking.controller;

import com.edumentor.booking.dto.CreateSlotRequest;
import com.edumentor.booking.dto.PageResponse;
import com.edumentor.booking.dto.SlotResponse;
import com.edumentor.booking.security.AuthenticatedUser;
import com.edumentor.booking.service.SlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Slots")
@SecurityRequirement(name = "bearerAuth")
public class SlotController {

    private final SlotService slotService;

    @PostMapping("/slots")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Create a counselling slot (APPROVED mentors only)")
    public SlotResponse createSlot(@AuthenticationPrincipal AuthenticatedUser user,
                                   @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                   @Valid @RequestBody CreateSlotRequest request) {
        return slotService.createSlot(user.userId(), authorization, request);
    }

    @GetMapping("/slots/mine")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "List my slots with their status")
    public PageResponse<SlotResponse> mySlots(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return slotService.listOwnSlots(user.userId(), page, size);
    }

    @DeleteMapping("/slots/{id}")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Cancel one of my AVAILABLE slots")
    public SlotResponse cancelSlot(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return slotService.cancelSlot(user.userId(), id);
    }

    @GetMapping("/mentors/{mentorId}/slots")
    @Operation(summary = "List a mentor's future AVAILABLE slots")
    public List<SlotResponse> availableSlots(@PathVariable Long mentorId,
                                             @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return slotService.listAvailableSlots(mentorId, authorization);
    }
}