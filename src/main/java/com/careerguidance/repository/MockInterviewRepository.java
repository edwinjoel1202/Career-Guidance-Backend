package com.careerguidance.repository;

import com.careerguidance.model.MockInterview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MockInterviewRepository extends JpaRepository<MockInterview, Long> {
    List<MockInterview> findByUserIdOrderByCreatedAtDesc(Long userId);
}
