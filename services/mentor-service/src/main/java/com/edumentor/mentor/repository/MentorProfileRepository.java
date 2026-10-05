package com.edumentor.mentor.repository;

import com.edumentor.mentor.entity.MentorProfile;
import com.edumentor.mentor.entity.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MentorProfileRepository
        extends JpaRepository<MentorProfile, Long>, JpaSpecificationExecutor<MentorProfile> {

    Optional<MentorProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    Optional<MentorProfile> findByIdAndVerificationStatus(Long id, VerificationStatus status);

    Page<MentorProfile> findByVerificationStatus(VerificationStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("update MentorProfile p set p.rating = :rating, p.reviewCount = :count "
            + "where p.id = :id and p.reviewCount <= :count")
    int updateRating(@Param("id") Long id, @Param("rating") double rating, @Param("count") int count);
}