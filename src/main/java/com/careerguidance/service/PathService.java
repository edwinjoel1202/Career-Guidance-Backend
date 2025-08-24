package com.careerguidance.service;

import com.careerguidance.exception.NotFoundException;
import com.careerguidance.model.LearningPath;
import com.careerguidance.model.PathItem;
import com.careerguidance.model.User;
import com.careerguidance.repository.LearningPathRepository;
import com.careerguidance.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class PathService {

    private final LearningPathRepository pathRepo;
    private final UserRepository userRepo;

    public PathService(LearningPathRepository pathRepo, UserRepository userRepo) {
        this.pathRepo = pathRepo;
        this.userRepo = userRepo;
    }

    public List<LearningPath> listForUser(Long userId) {
        return pathRepo.findByUserId(userId);
    }

    public LearningPath createPath(Long userId, String domain, List<Map<String, Object>> items) {
        User user = userRepo.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        LearningPath lp = new LearningPath();
        lp.setDomain(domain);
        lp.setUser(user);

        LocalDate start = LocalDate.now();
        List<PathItem> pathItems = new ArrayList<>();

        for (Map<String, Object> it : items) {
            String topic = String.valueOf(it.get("topic"));
            int duration = ((Number) it.get("duration")).intValue();
            LocalDate end = start.plusDays(duration);

            PathItem pi = new PathItem();
            pi.setTopic(topic);
            pi.setDuration(duration);
            pi.setStartDate(start.toString());
            pi.setEndDate(end.toString());
            pi.setStatus("pending");
            pi.setAssessmentResult(null);

            pathItems.add(pi);
            start = end;
        }
        lp.setPath(pathItems);
        return pathRepo.save(lp);
    }

    public LearningPath getByIdForUser(Long pathId, Long userId) {
        LearningPath lp = pathRepo.findById(pathId).orElseThrow(() -> new NotFoundException("Path not found"));
        if (!lp.getUser().getId().equals(userId)) {
            throw new NotFoundException("Path not found for user");
        }
        return lp;
    }

    public LearningPath updatePath(Long pathId, Long userId, List<PathItem> items) {
        LearningPath lp = getByIdForUser(pathId, userId);
        lp.setPath(items);
        return pathRepo.save(lp);
    }
}
