package com.edumentor.booking.repository;

import com.edumentor.booking.entity.Booking;
import com.edumentor.booking.entity.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    /** Scalar lookup used to lock the slot before the booking (slot first, always). */
    @Query("select b.slotId from Booking b where b.id = :id")
    Optional<Long> findSlotIdById(@Param("id") Long id);

    Optional<Booking> findByActiveSlotId(Long activeSlotId);

    long countByStudentIdAndStatus(Long studentId, BookingStatus status);

    Page<Booking> findByStudentId(Long studentId, Pageable pageable);

    Page<Booking> findByStudentIdAndStatus(Long studentId, BookingStatus status, Pageable pageable);

    Page<Booking> findByMentorUserId(Long mentorUserId, Pageable pageable);

    Page<Booking> findByMentorUserIdAndStatus(Long mentorUserId, BookingStatus status, Pageable pageable);

    @Query("select b.id from Booking b where b.status = :status and b.holdExpiresAt < :now "
            + "order by b.holdExpiresAt asc")
    List<Long> findExpiredHoldIds(@Param("status") BookingStatus status, @Param("now") Instant now,
                                  Pageable pageable);

    @Query("select b.id from Booking b where b.status = :status and b.reminderSent = false "
            + "and b.sessionStart > :from and b.sessionStart <= :to order by b.sessionStart asc")
    List<Long> findReminderCandidateIds(@Param("status") BookingStatus status, @Param("from") Instant from,
                                        @Param("to") Instant to, Pageable pageable);
}