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

    public LearningPath updateItemNotes(Long pathId, Long userId, int index, String notes) {
        LearningPath lp = getByIdForUser(pathId, userId);
        List<PathItem> list = lp.getPath();
        if (index < 0 || index >= list.size()) throw new NotFoundException("Item not found");
        PathItem item = list.get(index);
        item.setNotes(notes);
        return pathRepo.save(lp);
    }

    // +++ PATCH: add simple stats DTO-ish structure +++
    public Map<String,Object> computeUserStats(Long userId) {
        List<LearningPath> paths = listForUser(userId);
        int total = 0, completed = 0, pending = 0, overdue = 0;

        LocalDate today = LocalDate.now();
        for (LearningPath lp : paths) {
            for (PathItem it : lp.getPath()) {
                total++;
                String status = (it.getStatus() == null ? "pending" : it.getStatus());
                LocalDate end = it.getEndDate() == null ? today : LocalDate.parse(it.getEndDate());
                if ("completed".equalsIgnoreCase(status)) completed++;
                else if (end.isBefore(today)) overdue++;
                else pending++;
            }
        }

        // very simple streak: count consecutive days with at least one completed item ending that day
        int streak = 0;
        LocalDate d = today;
        while (true) {
            LocalDate finalD = d;
            boolean any = paths.stream().flatMap(p -> p.getPath().stream())
                    .anyMatch(it -> "completed".equalsIgnoreCase(it.getStatus())
                            && it.getEndDate() != null
                            && LocalDate.parse(it.getEndDate()).equals(finalD));
            if (any) { streak++; d = d.minusDays(1); } else break;
        }

        double progress = total == 0 ? 0 : (completed * 100.0 / total);
        return Map.of(
                "total", total,
                "completed", completed,
                "pending", pending,
                "overdue", overdue,
                "progress", progress,
                "streak", streak
        );
    }
}
