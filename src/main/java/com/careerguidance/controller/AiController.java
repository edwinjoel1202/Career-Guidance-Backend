package com.careerguidance.controller;

import com.careerguidance.service.AiService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService ai;

    public AiController(AiService ai) { this.ai = ai; }

    @PostMapping("/generate-path")
    public ResponseEntity<JsonNode> generatePath(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ai.generateLearningPath(body.get("domain")));
    }

    @PostMapping("/generate-assessment")
    public ResponseEntity<JsonNode> generateAssessment(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ai.generateAssessment(body.get("topic")));
    }

    @PostMapping("/evaluate-assessment")
    public ResponseEntity<JsonNode> evaluateAssessment(@RequestBody Map<String, Object> body) {
        String topic = (String) body.get("topic");
        String submission = String.valueOf(body.get("submissionJson"));
        return ResponseEntity.ok(ai.evaluateAssessment(topic, submission));
    }

    @PostMapping("/explain")
    public ResponseEntity<Map<String, String>> explain(@RequestBody Map<String, String> body) {
        String text = ai.explainTopic(body.get("domain"), body.get("topic"));
        return ResponseEntity.ok(Map.of("explanation", text));
    }

    @PostMapping("/resources")
    public ResponseEntity<JsonNode> resources(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ai.suggestResources(body.get("topic")));
    }

    // Chat tutor
    @PostMapping("/chat")
    public ResponseEntity<Map<String,String>> chat(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String,String>> messages = (List<Map<String,String>>) body.get("messages");
        String reply = ai.chatTutor(messages == null ? List.of() : messages);
        return ResponseEntity.ok(Map.of("reply", reply));
    }

    // Skill gap / resume analyze
    @PostMapping("/skill-gap")
    public ResponseEntity<JsonNode> skillGap(@RequestBody Map<String,String> body) {
        String resumeText = body.getOrDefault("resume", "");
        String role = body.getOrDefault("targetRole", "Software Engineer");
        return ResponseEntity.ok(ai.analyzeSkillGap(resumeText, role));
    }

    // Mock interview generation
    @PostMapping("/mock-interview")
    public ResponseEntity<JsonNode> mockInterview(@RequestBody Map<String, Object> body) {
        String role = String.valueOf(body.getOrDefault("role", "Software Engineer"));
        int rounds = Integer.parseInt(String.valueOf(body.getOrDefault("rounds", "5")));
        return ResponseEntity.ok(ai.generateMockInterview(role, rounds));
    }

    // Flashcards
    @PostMapping("/flashcards")
    public ResponseEntity<JsonNode> flashcards(@RequestBody Map<String, Object> body) {
        String topic = String.valueOf(body.getOrDefault("topic", ""));
        int count = Integer.parseInt(String.valueOf(body.getOrDefault("count", "10")));
        return ResponseEntity.ok(ai.generateFlashcards(topic, count));
    }

    // Coding exercise
    @PostMapping("/coding-exercise")
    public ResponseEntity<JsonNode> codingExercise(@RequestBody Map<String, String> body) {
        String topic = body.getOrDefault("topic", "");
        return ResponseEntity.ok(ai.generateCodingExercise(topic));
    }
}
