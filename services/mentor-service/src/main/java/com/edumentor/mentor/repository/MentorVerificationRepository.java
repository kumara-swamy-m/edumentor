package com.edumentor.mentor.repository;

import com.edumentor.mentor.entity.MentorVerification;
import com.edumentor.mentor.entity.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MentorVerificationRepository extends JpaRepository<MentorVerification, Long> {

    List<MentorVerification> findByMentorIdOrderBySubmittedAtDescIdDesc(Long mentorId);

    Optional<MentorVerification> findFirstByMentorIdAndStatusOrderBySubmittedAtDescIdDesc(
            Long mentorId, VerificationStatus status);
}