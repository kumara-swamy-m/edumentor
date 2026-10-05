package com.edumentor.mentor.messaging;

import com.edumentor.mentor.entity.Exam;
import com.edumentor.mentor.entity.MentorProfile;
import com.edumentor.mentor.repository.MentorProfileRepository;
import com.edumentor.mentor.repository.MentorVerificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class RatingEventIntegrationTest {

    @Autowired
    private RatingEventHandler handler;
    @Autowired
    private MentorProfileRepository profileRepository;
    @Autowired
    private MentorVerificationRepository verificationRepository;

    private long mentorId;

    @BeforeEach
    void setUp() {
        verificationRepository.deleteAll();
        profileRepository.deleteAll();
        MentorProfile profile = new MentorProfile();
        profile.setUserId(100L);
        profile.setName("Rahul");
        profile.setCollege("RVCE");
        profile.setCourse("B.E.");
        profile.setBranch("CSE");
        profile.setYear(3);
        profile.setExamPath(Exam.KCET);
        profile.setLocation("Bengaluru");
        mentorId = profileRepository.save(profile).getId();
    }

    @Test
    void ratingEventUpdatesTheMentorProfile() {
        handler.handle(event(mentorId, "4.50", 2));

        MentorProfile profile = profileRepository.findById(mentorId).orElseThrow();
        assertThat(profile.getRating()).isEqualTo(4.5);
        assertThat(profile.getReviewCount()).isEqualTo(2);
    }

    @Test
    void replayedAndStaleEventsDoNotMoveTheRatingBackwards() {
        handler.handle(event(mentorId, "4.50", 2));
        handler.handle(event(mentorId, "4.50", 2));
        handler.handle(event(mentorId, "5.00", 1));

        MentorProfile profile = profileRepository.findById(mentorId).orElseThrow();
        assertThat(profile.getRating()).isEqualTo(4.5);
        assertThat(profile.getReviewCount()).isEqualTo(2);

        handler.handle(event(mentorId, "4.33", 3));
        assertThat(profileRepository.findById(mentorId).orElseThrow().getReviewCount()).isEqualTo(3);
    }

    @Test
    void unknownMentorAndUnrelatedEventsAreIgnored() {
        handler.handle(event(99999L, "4.00", 1));
        handler.handle("{\"eventType\":\"SOMETHING_ELSE\"}");

        assertThat(profileRepository.findById(mentorId).orElseThrow().getReviewCount()).isZero();
    }

    @Test
    void malformedOrImplausibleEventsAreRejectedForTheDeadLetterTopic() {
        assertThatThrownBy(() -> handler.handle("not json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle(event(mentorId, "7.00", 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle("{\"eventType\":\"MENTOR_RATING_UPDATED\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String event(long id, String average, int count) {
        return "{\"eventId\":\"e1\",\"eventType\":\"MENTOR_RATING_UPDATED\",\"correlationId\":\"corr-1\","
                + "\"mentorId\":" + id + ",\"averageRating\":" + average + ",\"reviewCount\":" + count + "}";
    }
}