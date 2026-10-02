package com.edumentor.notification.repository;

import com.edumentor.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsBySourceEventIdAndUserId(String sourceEventId, Long userId);

    Page<Notification> findByUserId(Long userId, Pageable pageable);
}