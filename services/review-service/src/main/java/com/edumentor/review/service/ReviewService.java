package com.edumentor.review.service;

import com.edumentor.review.client.BookingInfo;
import com.edumentor.review.dto.CreateReviewRequest;
import com.edumentor.review.dto.PageResponse;
import com.edumentor.review.dto.PublicReviewResponse;
import com.edumentor.review.dto.RatingSummaryResponse;
import com.edumentor.review.dto.ReviewResponse;
import com.edumentor.review.entity.Rating;
import com.edumentor.review.entity.Review;
import com.edumentor.review.exception.ApiException;
import com.edumentor.review.repository.RatingRepository;
import com.edumentor.review.repository.ReviewRepository;
import com.edumentor.review.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ReviewRepository reviewRepository;
    private final RatingRepository ratingRepository;
    private final BookingDirectory bookingDirectory;
    private final ReviewOutboxService outbox;
    private final TransactionTemplate transactionTemplate;

    public ReviewResponse createReview(AuthenticatedUser user, String authorization, CreateReviewRequest request) {
        BookingInfo booking = bookingDirectory.requireCompletedOwn(request.bookingId(), authorization,
                user.userId());
        if (reviewRepository.existsByBookingId(booking.id())) {
            throw ApiException.reviewAlreadyExists();
        }
        ensureRatingRow(booking.mentorId());

        try {
            Review saved = Objects.requireNonNull(
                    transactionTemplate.execute(status -> saveReview(user.userId(), booking, request)));
            log.info("Review created: reviewId={}, bookingId={}, mentorId={}", saved.getId(), saved.getBookingId(),
                    saved.getMentorId());
            return ReviewResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request for the same booking won: the unique booking_id is the last line of defence
            throw ApiException.reviewAlreadyExists();
        }
    }

    private Review saveReview(Long studentId, BookingInfo booking, CreateReviewRequest request) {
        // Lock the aggregate first: reviews of one mentor are serialized, and so are their events
        Rating rating = ratingRepository.findByMentorIdForUpdate(booking.mentorId())
                .orElseThrow(() -> new IllegalStateException("Rating row missing for mentor " + booking.mentorId()));

        Review review = new Review();
        review.setBookingId(booking.id());
        review.setMentorId(booking.mentorId());
        review.setStudentId(studentId);
        review.setRating(request.rating());
        String comment = request.comment() == null ? "" : request.comment().strip();
        review.setComment(comment.isEmpty() ? null : comment);
        Review saved = reviewRepository.saveAndFlush(review);

        rating.add(request.rating());
        outbox.ratingUpdated(saved, rating);
        return saved;
    }

    /** Creates the aggregate row in its own small transaction so a concurrent creation cannot poison the main one. */
    private void ensureRatingRow(Long mentorId) {
        if (ratingRepository.findByMentorId(mentorId).isPresent()) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(
                    status -> ratingRepository.saveAndFlush(Rating.forMentor(mentorId)));
        } catch (DataIntegrityViolationException ex) {
            // Created concurrently: fine
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicReviewResponse> listForMentor(Long mentorId, int page, int size) {
        return PageResponse.from(reviewRepository.findByMentorId(mentorId, pageable(page, size)),
                PublicReviewResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listMine(Long studentId, int page, int size) {
        return PageResponse.from(reviewRepository.findByStudentId(studentId, pageable(page, size)),
                ReviewResponse::from);
    }

    @Transactional(readOnly = true)
    public RatingSummaryResponse summary(Long mentorId) {
        return ratingRepository.findByMentorId(mentorId)
                .map(r -> new RatingSummaryResponse(mentorId, r.average(), r.getRatingCount()))
                .orElseGet(() -> new RatingSummaryResponse(mentorId, BigDecimal.ZERO.setScale(2), 0));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }
}