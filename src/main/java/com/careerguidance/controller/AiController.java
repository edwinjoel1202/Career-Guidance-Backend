package com.careerguidance.controller;

import com.careerguidance.service.AiService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
