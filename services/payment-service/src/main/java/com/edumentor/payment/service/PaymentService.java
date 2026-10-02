package com.edumentor.payment.service;

import com.edumentor.payment.client.BookingSummary;
import com.edumentor.payment.dto.CreatePaymentResult;
import com.edumentor.payment.dto.PageResponse;
import com.edumentor.payment.dto.PaymentResponse;
import com.edumentor.payment.entity.Payment;
import com.edumentor.payment.entity.PaymentStatus;
import com.edumentor.payment.entity.PaymentTransaction;
import com.edumentor.payment.entity.TransactionType;
import com.edumentor.payment.exception.ApiException;
import com.edumentor.payment.provider.CreateProviderPayment;
import com.edumentor.payment.provider.PaymentProvider;
import com.edumentor.payment.provider.ProviderException;
import com.edumentor.payment.provider.ProviderOutcome;
import com.edumentor.payment.provider.ProviderPayment;
import com.edumentor.payment.repository.PaymentRepository;
import com.edumentor.payment.repository.PaymentTransactionRepository;
import com.edumentor.payment.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final BookingDirectory bookingDirectory;
    private final PaymentProvider provider;
    private final PaymentProcessor paymentProcessor;
    private final TransactionTemplate transactionTemplate;

    // ---------- Create (idempotent) ----------

    public CreatePaymentResult createPayment(AuthenticatedUser user, String authorization, String idempotencyKey,
                                             Long bookingId) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw ApiException.invalidIdempotencyKey();
        }

        // A repeated key never re-validates or re-charges: it returns what was created the first time
        Optional<Payment> existing = paymentRepository.findByStudentIdAndIdempotencyKey(user.userId(), idempotencyKey);
        if (existing.isPresent()) {
            return resume(existing.get(), bookingId, true);
        }

        BookingSummary booking = bookingDirectory.requirePayable(bookingId, authorization, user.userId());
        if (paymentRepository.findByBookingId(bookingId).isPresent()) {
            throw ApiException.paymentAlreadyExists();
        }

        Payment payment;
        try {
            payment = insertPayment(user.userId(), idempotencyKey, booking);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request with the same key (or for the same booking) won the race
            Optional<Payment> raced = paymentRepository.findByStudentIdAndIdempotencyKey(user.userId(), idempotencyKey);
            if (raced.isPresent()) {
                return resume(raced.get(), bookingId, true);
            }
            throw ApiException.paymentAlreadyExists();
        }
        log.info("Payment created: paymentId={}, bookingId={}, studentId={}", payment.getId(), bookingId,
                user.userId());
        return resume(payment, bookingId, false);
    }

    private Payment insertPayment(Long studentId, String idempotencyKey, BookingSummary booking) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            Payment payment = new Payment();
            payment.setBookingId(booking.id());
            payment.setStudentId(studentId);
            payment.setAmount(booking.price());
            payment.setCurrency(booking.currency());
            payment.setStatus(PaymentStatus.CREATED);
            payment.setProvider(provider.name());
            payment.setIdempotencyKey(idempotencyKey);
            Payment saved = paymentRepository.saveAndFlush(payment);
            transactionRepository.save(new PaymentTransaction(saved.getId(), TransactionType.PAYMENT_CREATED,
                    PaymentStatus.CREATED, "API", null, "Payment created"));
            return saved;
        }));
    }

    private CreatePaymentResult resume(Payment payment, Long bookingId, boolean replayed) {
        if (!payment.getBookingId().equals(bookingId)) {
            throw ApiException.idempotencyKeyReused();
        }
        Payment current = payment;
        if (current.getStatus() == PaymentStatus.CREATED && current.getProviderPaymentId() == null) {
            current = registerWithProvider(current.getId());
        }
        return new CreatePaymentResult(PaymentResponse.from(current, true), replayed);
    }

    /** Calls the provider outside any transaction, then stores the result under a row lock. */
    private Payment registerWithProvider(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(ApiException::paymentNotFound);
        ProviderPayment providerPayment;
        try {
            providerPayment = provider.createPayment(new CreateProviderPayment(payment.getId(),
                    payment.getBookingId(), payment.getStudentId(), payment.getAmount(), payment.getCurrency()));
        } catch (ProviderException ex) {
            log.warn("Provider payment creation failed: paymentId={}, cause={}", paymentId,
                    ex.getCause() == null ? ex.getClass().getSimpleName() : ex.getCause().getClass().getSimpleName());
            throw ApiException.providerUnavailable();
        }

        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            Payment locked = paymentRepository.findByIdForUpdate(paymentId).orElseThrow(ApiException::paymentNotFound);
            if (locked.getStatus() == PaymentStatus.CREATED) {
                locked.setProviderPaymentId(providerPayment.providerPaymentId());
                locked.setClientSecret(providerPayment.clientSecret());
                locked.setStatus(PaymentStatus.PENDING);
                transactionRepository.save(new PaymentTransaction(locked.getId(), TransactionType.PROVIDER_REGISTERED,
                        PaymentStatus.PENDING, "API", null, "Registered with provider " + provider.name()));
                log.info("Payment registered with provider: paymentId={}, provider={}", paymentId, provider.name());
            }
            return locked;
        }));
    }

    // ---------- Verify (server asks the provider; the client is never trusted) ----------

    public PaymentResponse verify(AuthenticatedUser user, Long paymentId) {
        Payment payment = requireOwn(user, paymentId);
        if (payment.getProviderPaymentId() == null || payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            return PaymentResponse.from(payment, true);
        }

        ProviderOutcome outcome;
        try {
            outcome = provider.fetchStatus(payment.getProviderPaymentId());
        } catch (ProviderException ex) {
            log.warn("Provider status lookup failed: paymentId={}", paymentId);
            throw ApiException.providerUnavailable();
        }
        if (outcome == ProviderOutcome.PENDING || outcome == ProviderOutcome.IGNORED) {
            return PaymentResponse.from(payment, true);
        }
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            Payment locked = paymentRepository.findByIdForUpdate(paymentId).orElseThrow(ApiException::paymentNotFound);
            paymentProcessor.apply(locked, outcome, "VERIFY", null, "Provider reports failure");
            return PaymentResponse.from(locked, true);
        }));
    }

    // ---------- Queries ----------

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(AuthenticatedUser user, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(ApiException::paymentNotFound);
        boolean admin = "ADMIN".equals(user.role());
        if (!admin && !payment.getStudentId().equals(user.userId())) {
            throw ApiException.paymentNotFound();
        }
        return PaymentResponse.from(payment, !admin);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> listMine(Long studentId, int page, int size) {
        return PageResponse.from(paymentRepository.findByStudentId(studentId, pageable(page, size)),
                p -> PaymentResponse.from(p, true));
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> listAll(PaymentStatus status, int page, int size) {
        Pageable pageable = pageable(page, size);
        return PageResponse.from(status == null
                        ? paymentRepository.findAll(pageable)
                        : paymentRepository.findByStatus(status, pageable),
                p -> PaymentResponse.from(p, false));
    }

    private Payment requireOwn(AuthenticatedUser user, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(ApiException::paymentNotFound);
        if (!payment.getStudentId().equals(user.userId())) {
            throw ApiException.paymentNotFound();
        }
        return payment;
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }
}