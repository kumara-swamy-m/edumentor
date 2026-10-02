package com.edumentor.mentor.repository;

import com.edumentor.mentor.entity.Exam;
import com.edumentor.mentor.entity.MentorProfile;
import com.edumentor.mentor.entity.VerificationStatus;
import org.springframework.data.jpa.domain.Specification;

public final class MentorSpecifications {

    private MentorSpecifications() {
    }

    public static Specification<MentorProfile> approved() {
        return (root, query, cb) -> cb.equal(root.get("verificationStatus"), VerificationStatus.APPROVED);
    }

    public static Specification<MentorProfile> hasExam(Exam exam) {
        if (exam == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("examPath"), exam);
    }

    public static Specification<MentorProfile> collegeContains(String college) {
        return containsIgnoreCase("college", college);
    }

    public static Specification<MentorProfile> branchContains(String branch) {
        return containsIgnoreCase("branch", branch);
    }

    public static Specification<MentorProfile> locationContains(String location) {
        return containsIgnoreCase("location", location);
    }

    public static Specification<MentorProfile> ratingAtLeast(Double minRating) {
        if (minRating == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.<Double>get("rating"), minRating);
    }

    private static Specification<MentorProfile> containsIgnoreCase(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(field)), pattern);
    }
}