package com.careerguidance.controller;

import com.careerguidance.dto.*;
import com.careerguidance.model.LearningPath;
import com.careerguidance.model.PathItem;
import com.careerguidance.model.User;
import com.careerguidance.service.AiService;
import com.careerguidance.service.PathService;
import com.careerguidance.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/paths")
public class PathController {

    private final PathService pathService;
    private final UserService userService;
    private final AiService ai;

    public PathController(PathService pathService, UserService userService, AiService ai) {
        this.pathService = pathService;
        this.userService = userService;
        this.ai = ai;
    }

    private Long currentUserId(Authentication auth) {
        User u = userService.getByEmail(auth.getName());
        return u.getId();
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<LearningPath> list(Authentication auth) {
        return pathService.listForUser(currentUserId(auth));
    }

    @GetMapping("/{pathId}")
    @Transactional(readOnly = true)
    public LearningPath getOne(@PathVariable Long pathId, Authentication auth) {
        return pathService.getByIdForUser(pathId, currentUserId(auth));
    }

    @PostMapping
    public LearningPath create(@Valid @RequestBody PathCreateRequest req, Authentication auth) {
        // Use provided path items (from frontend after Gemini) OR generate here if empty
        List<Map<String, Object>> items = new ArrayList<>();
        if (req.getPath() != null && !req.getPath().isEmpty()) {
            for (PathItem pi : req.getPath()) {
                items.add(Map.of("topic", pi.getTopic(), "duration", pi.getDuration()));
            }
        } else {
            JsonNode gen = ai.generateLearningPath(req.getDomain());
            gen.forEach(node -> items.add(Map.of(
                    "topic", node.path("topic").asText(),
                    "duration", node.path("duration").asInt(1)
            )));
        }
        return pathService.createPath(currentUserId(auth), req.getDomain(), items);
    }

    @PutMapping("/{pathId}")
    public LearningPath update(@PathVariable Long pathId, @Valid @RequestBody PathUpdateRequest req, Authentication auth) {
        return pathService.updatePath(pathId, currentUserId(auth), req.getPath());
    }

    // Helpers similar to the HTML app features

    @PostMapping("/{pathId}/assessment")
    public ResponseEntity<JsonNode> generateAssessment(@PathVariable Long pathId, @RequestParam int topicIndex, Authentication auth) {
        LearningPath lp = pathService.getByIdForUser(pathId, currentUserId(auth));
        String topic = lp.getPath().get(topicIndex).getTopic();
        return ResponseEntity.ok(ai.generateAssessment(topic));
    }

    @PostMapping("/{pathId}/assessment/evaluate")
    public LearningPath evaluateAssessment(
            @PathVariable Long pathId,
            @RequestParam int topicIndex,
            @RequestBody AssessmentSubmission submission,
            Authentication auth) {

        LearningPath lp = pathService.getByIdForUser(pathId, currentUserId(auth));
        PathItem item = lp.getPath().get(topicIndex);

        try {
            String submissionJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(submission.getAnswers());
            var result = ai.evaluateAssessment(item.getTopic(), submissionJson);

            int score = result.path("score").asInt(0);
            int outOf = result.path("outOf").asInt(10);
            double percentage = outOf == 0 ? 0 : (100.0 * score / outOf);

            // Save assessment record
            // Note: requires AssessmentRepository - autowire it in controller if needed
            item.setAssessmentResult(result.toString());

            // Passing criteria: 7 correct out of 10 (or >=70% if different count)
            boolean passed;
            if (outOf >= 10) {
                passed = score >= 7;
            } else {
                passed = percentage >= 70.0;
            }

            if (passed) {
                item.setStatus("completed");
                // move to next topic automatically: mark next as pending (if exists)
                if (topicIndex + 1 < lp.getPath().size()) {
                    lp.getPath().get(topicIndex + 1).setStatus("pending");
                }
            } else {
                item.setStatus("failed");
                // keep same topic as pending/failed; user must retake
            }

            return pathService.updatePath(pathId, currentUserId(auth), lp.getPath());
        } catch (Exception e) {
            throw new RuntimeException("Evaluation failed: " + e.getMessage());
        }
    }

    @PostMapping("/{pathId}/explain")
    public ResponseEntity<Map<String, String>> explain(@PathVariable Long pathId, @RequestParam int topicIndex, Authentication auth) {
        LearningPath lp = pathService.getByIdForUser(pathId, currentUserId(auth));
        String text = ai.explainTopic(lp.getDomain(), lp.getPath().get(topicIndex).getTopic());
        return ResponseEntity.ok(Map.of("explanation", text));
    }

    @PostMapping("/{pathId}/resources")
    public ResponseEntity<JsonNode> suggestResources(@PathVariable Long pathId, @RequestParam int topicIndex, Authentication auth) {
        LearningPath lp = pathService.getByIdForUser(pathId, currentUserId(auth));
        return ResponseEntity.ok(ai.suggestResources(lp.getPath().get(topicIndex).getTopic()));
    }

    @PostMapping("/{pathId}/regenerate")
    public LearningPath regenerate(@PathVariable Long pathId, @RequestBody RegenerateRequest req, Authentication auth) {
        LearningPath lp = pathService.getByIdForUser(pathId, currentUserId(auth));

        // Build remaining topics according to the reason
        List<PathItem> current = lp.getPath();
        List<PathItem> completed = new ArrayList<>(current.subList(0, Math.max(0, req.getFromIndex())));
        List<PathItem> remaining;

        if ("failure".equalsIgnoreCase(req.getReason())) {
            PathItem failed = current.get(req.getFromIndex());
            failed.setStatus("pending");
            remaining = new ArrayList<>();
            remaining.add(failed);
            remaining.addAll(current.subList(req.getFromIndex() + 1, current.size()));
        } else {
            remaining = new ArrayList<>(current.subList(req.getFromIndex(), current.size()));
        }

        List<String> remainingTopics = remaining.stream().map(PathItem::getTopic).collect(Collectors.toList());
        var regen = ai.regenerateSchedule(remainingTopics);

        // Rebuild dates from today
        List<PathItem> newRemaining = new ArrayList<>();
        java.time.LocalDate start = LocalDate.now();
        for (var node : regen) {
            String topic = node.path("topic").asText();
            int duration = node.path("duration").asInt(1);
            var end = start.plusDays(duration);

            PathItem pi = new PathItem();
            pi.setTopic(topic);
            pi.setDuration(duration);
            pi.setStartDate(start.toString());
            pi.setEndDate(end.toString());
            pi.setStatus("pending");
            pi.setAssessmentResult(null);
            newRemaining.add(pi);

            start = end;
        }

        List<PathItem> merged = new ArrayList<>(completed);
        merged.addAll(newRemaining);
        return pathService.updatePath(pathId, currentUserId(auth), merged);
    }

    @PutMapping("/{pathId}/items/{index}/notes")
    public LearningPath updateItemNotes(
            @PathVariable Long pathId,
            @PathVariable int index,
            @RequestBody Map<String,String> body,
            Authentication auth
    ) {
        String notes = body.getOrDefault("notes", "");
        return pathService.updateItemNotes(pathId, currentUserId(auth), index, notes);
    }
}
