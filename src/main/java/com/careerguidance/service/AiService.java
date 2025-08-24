package com.careerguidance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class AiService {

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${ai.gemini.model:gemini-1.5-flash}")
    private String model;

    @Value("${ai.gemini.apiKey:}")
    private String apiKey;

    private String endpoint() {
        return "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
    }

    private JsonNode callGemini(String prompt, boolean expectJson) {
        try {
            Map<String, Object> generationConfig = expectJson
                    ? Map.of("responseMimeType", "application/json")
                    : Map.of();

            Map<String, Object> payload = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", generationConfig
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> resp = rest.exchange(
                    endpoint(), HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Gemini error: " + resp.getStatusCode());
            }

            JsonNode root = mapper.readTree(resp.getBody());
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode textNode = candidates.get(0).path("content").path("parts").get(0).path("text");
                if (textNode.isMissingNode()) throw new RuntimeException("Gemini: no text part");
                String text = textNode.asText();
                if (expectJson) {
                    return mapper.readTree(text);
                }
                return mapper.createObjectNode().put("text", text);
            }
            throw new RuntimeException("Gemini: unexpected response");
        } catch (Exception e) {
            throw new RuntimeException("Gemini call failed: " + e.getMessage(), e);
        }
    }

    public JsonNode generateLearningPath(String domain) {
        String prompt = "Create a detailed, structured learning path for a beginner in \"" + domain + "\". " +
                "The path should be a JSON array; each item has keys: topic (string) and duration (number days).";
        return callGemini(prompt, true);
    }

    public JsonNode generateAssessment(String topic) {
        String prompt = "Create a short assessment with 5 multiple-choice questions for the topic \"" + topic + "\". " +
                "Each question has 4 options (A,B,C,D) and one correct answer. Return a JSON array of objects: " +
                "{ question, options: {A,B,C,D}, answer }.";
        return callGemini(prompt, true);
    }

    public JsonNode evaluateAssessment(String topic, String submissionJson) {
        // Note: For multiple-choice, could compute score directly, but keeping AI for potential open-ended questions
        String prompt = "As an expert evaluator, analyze the following quiz submission. The user was tested on \"" + topic + "\". " +
                "Submission JSON: " + submissionJson + ". " +
                "Return JSON object: { \"score\": number, \"evaluation\": [{question, correctAnswer, userAnswer, isCorrect}] }";
        return callGemini(prompt, true);
    }

    public String explainTopic(String domain, String topic) {
        String prompt = "Explain the concept of \"" + topic + "\" for a beginner in the field of " + domain + ". " +
                "Keep it concise, easy to understand, and use paragraphs.";
        return callGemini(prompt, false).path("text").asText();
    }

    public JsonNode suggestResources(String topic) {
        String prompt = "Suggest 3-5 high-quality online resources for a beginner learning about \"" + topic + "\". " +
                "Return a JSON array of { title, type, description }.";
        return callGemini(prompt, true);
    }

    public JsonNode regenerateSchedule(List<String> remainingTopics) {
        String prompt = "I have a list of remaining topics to learn: " + remainingTopics + ". " +
                "Create a new learning schedule starting from today. Return a JSON array of objects: " +
                "{ topic, duration }.";
        return callGemini(prompt, true);
    }
}