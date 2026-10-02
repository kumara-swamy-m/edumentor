package com.edumentor.mentor.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "mentor_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uk_mentor_user", columnNames = "user_id"),
        indexes = @Index(name = "idx_mentor_status", columnList = "verification_status"))
@Getter
@Setter
@NoArgsConstructor
public class MentorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reference to auth-service user (no cross-database foreign key). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 150)
    private String college;

    @Column(nullable = false, length = 100)
    private String course;

    @Column(nullable = false, length = 100)
    private String branch;

    @Column(name = "study_year", nullable = false)
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_path", nullable = false, length = 10)
    private Exam examPath;

    @Column(name = "exam_rank")
    private Integer rank;

    @Column(length = 2000)
    private String bio;

    @Column(nullable = false, length = 100)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    /** Latest admin decision comment (visible to the mentor and admins only). */
    @Column(name = "verification_note", length = 500)
    private String verificationNote;

    @Column(nullable = false)
    private double rating;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "mentor", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @OrderBy("id ASC")
    private List<MentorExpertise> expertise = new ArrayList<>();

    @OneToMany(mappedBy = "mentor", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @OrderBy("id ASC")
    private List<MentorAvailability> availability = new ArrayList<>();

    /** Replaces expertise topics, trimming and de-duplicating case-insensitively. */
    public void replaceExpertise(Collection<String> topics) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String topic : topics) {
            String value = topic.trim();
            if (!value.isEmpty()) {
                unique.putIfAbsent(value.toLowerCase(), value);
            }
        }
        expertise.clear();
        unique.values().forEach(value -> expertise.add(new MentorExpertise(this, value)));
    }
}