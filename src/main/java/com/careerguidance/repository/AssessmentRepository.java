package com.careerguidance.repository;

import com.careerguidance.model.AssessmentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssessmentRepository extends JpaRepository<AssessmentRecord, Long> {
    List<AssessmentRecord> findByUserIdOrderByCreatedAtDesc(Long userId);
}
