package com.careerguidance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/*
 AiService: keeps Gemini REST wiring.
 - generateAssessment now produces 10 questions.
 - callGemini(...) unchanged in core behavior (returns text or parsed JSON when expectJson true)
*/

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
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))) ,
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
                "Return a JSON array; each item: {\"topic\":\"...\",\"duration\":<days>} ";
        return callGemini(prompt, true);
    }

    public JsonNode generateAssessment(String topic) {
        String prompt = "Create a comprehensive assessment with 10 multiple-choice questions for the topic \"" + topic + "\". " +
                "Each question should have 4 options (A,B,C,D) and one correct answer. Return JSON array: " +
                "{\"question\":..., \"options\":{\"A\":\"...\",\"B\":\"...\",\"C\":\"...\",\"D\":\"...\"}, \"answer\":\"A|B|C|D\" }";
        return callGemini(prompt, true);
    }

    public JsonNode evaluateAssessment(String topic, String submissionJson) {
        String prompt = "You are an expert evaluator. Topic: \"" + topic + "\". User submission JSON: " + submissionJson + ". " +
                "Return JSON {\"score\": number, \"outOf\": number, \"percentage\": number, \"evaluation\": [{\"question\":..., \"correctAnswer\":..., \"userAnswer\":..., \"isCorrect\":true|false}, ...] }";
        return callGemini(prompt, true);
    }

    public String explainTopic(String domain, String topic) {
        String prompt = "Explain \"" + topic + "\" for a beginner in " + domain + ". Keep it concise and clear in 3 short paragraphs.";
        return callGemini(prompt, false).path("text").asText();
    }

    public JsonNode suggestResources(String topic) {
        String prompt = "Suggest 3 high-quality resources for \"" + topic + "\". Return JSON array [{\"title\":\"...\",\"type\":\"(article/course/video)\",\"url\":\"...\",\"description\":\"...\"}] - include a url if possible.";
        return callGemini(prompt, true);
    }

    public JsonNode regenerateSchedule(List<String> remainingTopics) {
        String prompt = "I have remaining topics: " + remainingTopics + ". Create a schedule starting today and return JSON array [{\"topic\":\"...\",\"duration\":<days>}].";
        return callGemini(prompt, true);
    }

    public String chatTutor(List<Map<String, String>> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a helpful study tutor. Keep responses friendly and brief.\n");
        for (Map<String, String> m : messages) {
            sb.append(m.getOrDefault("role", "user")).append(": ").append(m.getOrDefault("content", "")).append("\n");
        }
        sb.append("Assistant:");
        return callGemini(sb.toString(), false).path("text").asText();
    }

    public JsonNode analyzeSkillGap(String resumeText, String targetRole) {
        String prompt = "You are a career analyst. User resume: " + resumeText + ". Target role: " + targetRole + ". " +
                "Return JSON {\"missingSkills\":[{\"skill\":\"...\",\"importance\":\"high|medium|low\",\"suggestedResources\":[{\"title\":\"...\",\"url\":\"...\"}]}], \"recommendedPath\":[{\"topic\":\"...\",\"duration\":days}]}";
        return callGemini(prompt, true);
    }

    public JsonNode generateMockInterview(String targetRole, int rounds) {
        String prompt = "Generate " + rounds + " interview questions for role: " + targetRole + ". For each: {question, difficulty: 'easy'|'medium'|'hard', followups:[...]} Return JSON array.";
        return callGemini(prompt, true);
    }

    public JsonNode generateFlashcards(String topic, int count) {
        String prompt = "Create " + count + " flashcards for topic \"" + topic + "\". Return JSON array [{\"q\":\"...\",\"a\":\"...\"}].";
        return callGemini(prompt, true);
    }

    public JsonNode generateCodingExercise(String topic) {
        String prompt = "Generate a coding exercise suitable for interview practice about \"" + topic + "\". Return JSON {\"title\",\"description\",\"functionSignature\",\"language\":\"java|python|js\",\"testcases\":[{\"input\":\"...\",\"output\":\"...\"}]}";
        return callGemini(prompt, true);
    }
}
