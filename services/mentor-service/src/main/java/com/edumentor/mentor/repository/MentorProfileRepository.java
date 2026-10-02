package com.edumentor.mentor.repository;

import com.edumentor.mentor.entity.MentorProfile;
import com.edumentor.mentor.entity.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface MentorProfileRepository
        extends JpaRepository<MentorProfile, Long>, JpaSpecificationExecutor<MentorProfile> {

    Optional<MentorProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    Optional<MentorProfile> findByIdAndVerificationStatus(Long id, VerificationStatus status);

    Page<MentorProfile> findByVerificationStatus(VerificationStatus status, Pageable pageable);
}