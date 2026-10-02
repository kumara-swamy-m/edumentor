package com.edumentor.payment.repository;

import com.edumentor.payment.entity.Payment;
import com.edumentor.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByStudentIdAndIdempotencyKey(Long studentId, String idempotencyKey);

    Optional<Payment> findByBookingId(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.provider = :provider and p.providerPaymentId = :providerPaymentId")
    Optional<Payment> findByProviderAndProviderPaymentIdForUpdate(@Param("provider") String provider,
                                                                  @Param("providerPaymentId") String providerPaymentId);

    Page<Payment> findByStudentId(Long studentId, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);
}