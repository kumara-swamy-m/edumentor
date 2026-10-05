package com.edumentor.mentor.service;

import com.edumentor.mentor.repository.MentorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MentorRatingService {

    private final MentorProfileRepository profileRepository;

    /** Returns true when the rating was applied; false for stale events and unknown mentors. */
    @Transactional
    public boolean applyRating(Long mentorId, double averageRating, int reviewCount) {
        int updated = profileRepository.updateRating(mentorId, averageRating, reviewCount);
        if (updated > 0) {
            log.info("Mentor rating updated: mentorId={}, rating={}, reviewCount={}", mentorId, averageRating,
                    reviewCount);
            return true;
        }
        if (profileRepository.existsById(mentorId)) {
            log.info("Stale rating event ignored: mentorId={}, reviewCount={}", mentorId, reviewCount);
        } else {
            log.warn("Rating event for unknown mentor ignored: mentorId={}", mentorId);
        }
        return false;
    }
}