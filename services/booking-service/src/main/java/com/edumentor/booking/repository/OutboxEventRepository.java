package com.edumentor.booking.repository;

import com.edumentor.booking.entity.OutboxEvent;
import com.edumentor.booking.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop100ByStatusOrderByIdAsc(OutboxStatus status);
}