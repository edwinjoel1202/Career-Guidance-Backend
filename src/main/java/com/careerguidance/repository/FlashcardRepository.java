package com.careerguidance.repository;

import com.careerguidance.model.FlashcardCollection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlashcardRepository extends JpaRepository<FlashcardCollection, Long> {
    List<FlashcardCollection> findByUserIdOrderByCreatedAtDesc(Long userId);
}
