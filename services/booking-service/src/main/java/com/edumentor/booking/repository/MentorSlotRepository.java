package com.edumentor.booking.repository;

import com.edumentor.booking.entity.MentorSlot;
import com.edumentor.booking.entity.SlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MentorSlotRepository extends JpaRepository<MentorSlot, Long> {

    /** SELECT ... FOR UPDATE: concurrent requests for the same slot queue up behind this lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MentorSlot s where s.id = :id")
    Optional<MentorSlot> findByIdForUpdate(@Param("id") Long id);

    List<MentorSlot> findByMentorIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(
            Long mentorId, SlotStatus status, Instant after);

    Page<MentorSlot> findByMentorUserId(Long mentorUserId, Pageable pageable);

    /** Overlap test: existing.start < newEnd AND existing.end > newStart. */
    boolean existsByMentorIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            Long mentorId, Collection<SlotStatus> statuses, Instant newEnd, Instant newStart);

    @Modifying
    @Query("update MentorSlot s set s.status = :expired, s.updatedAt = :now "
            + "where s.status = :available and s.startTime <= :now")
    int expirePastAvailable(@Param("available") SlotStatus available,
                            @Param("expired") SlotStatus expired,
                            @Param("now") Instant now);
}