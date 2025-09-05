package com.careerguidance.controller;

import com.careerguidance.model.*;
import com.careerguidance.repository.*;
import com.careerguidance.service.AiService;
import com.careerguidance.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AiController (updated)
 *
 * Changes:
 *  - POST /api/ai/chat: robustly saves user messages and assistant replies into ChatSession/ChatMessage.
 *    It accepts either a "messages" array or a single "message" object. If sessionId is provided, it
 *    appends new user messages only (avoids duplicating existing saved messages). Then it constructs
 *    conversation history from DB and sends that to AI to get a reply. The assistant reply is saved.
 *
 *  - GET /api/ai/sessions: lists chat sessions for the current user (id, title, createdAt, lastPreview).
 *  - GET /api/ai/sessions/{id}: returns session details including ordered messages.
 *
 *  - PUT /api/ai/sessions/{id}: rename a session (body: { "title": "..." }) — ownership enforced.
 *  - DELETE /api/ai/sessions/{id}: delete session (and messages) — ownership enforced.
 *
 *  - Methods are transactional where we mutate DB to ensure consistent saves.
 *
 *  This file preserves existing other endpoints and behavior.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService ai;
    private final ObjectMapper mapper = new ObjectMapper();

    private final ChatSessionRepository chatSessionRepo;
    private final ChatMessageRepository chatMessageRepo;
    private final MockInterviewRepository mockInterviewRepo;
    private final RecommendationRepository recommendationRepo;
    private final FlashcardRepository flashcardRepo;
    private final AssessmentRepository assessmentRepo;
    private final UserService userService;

    private final Parser mdParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    public AiController(AiService ai,
                        ChatSessionRepository chatSessionRepo,
                        ChatMessageRepository chatMessageRepo,
                        MockInterviewRepository mockInterviewRepo,
                        RecommendationRepository recommendationRepo,
                        FlashcardRepository flashcardRepo,
                        AssessmentRepository assessmentRepo,
                        UserService userService) {
        this.ai = ai;
        this.chatSessionRepo = chatSessionRepo;
        this.chatMessageRepo = chatMessageRepo;
        this.mockInterviewRepo = mockInterviewRepo;
        this.recommendationRepo = recommendationRepo;
        this.flashcardRepo = flashcardRepo;
        this.assessmentRepo = assessmentRepo;
        this.userService = userService;
    }

    // ---------------- existing endpoints unchanged (generate path etc.) ----------------

    @PostMapping("/generate-path")
    public ResponseEntity<JsonNode> generatePath(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ai.generateLearningPath(body.get("domain")));
    }

    @PostMapping("/generate-assessment")
    public ResponseEntity<Map<String, Object>> generateAssessment(@RequestBody Map<String, Object> body, Authentication auth) {
        String topic = String.valueOf(body.get("topic"));
        JsonNode assessment = ai.generateAssessment(topic);

        // store generated assessment for the user (un-evaluated)
        var user = userService.getByEmail(auth.getName());
        AssessmentRecord ar = new AssessmentRecord();
        ar.setUser(user);
        ar.setTopic(topic);
        ar.setAssessmentJson(assessment.toString());
        ar.setQuestionCount(10);
        ar.setScore(0);
        ar.setPassed(false);
        ar.setTopicIndex(body.get("topicIndex") instanceof Number ? ((Number) body.get("topicIndex")).intValue() : null);
        if (body.get("pathId") != null) {
            ar.setLearningPathId(String.valueOf(body.get("pathId")));
        }
        assessmentRepo.save(ar);

        return ResponseEntity.ok(Map.of(
                "assessment", assessment,
                "assessmentId", ar.getId()
        ));
    }

    @PostMapping("/evaluate-assessment")
    public ResponseEntity<JsonNode> evaluateAssessment(@RequestBody Map<String, Object> body, Authentication auth) {
        String topic = String.valueOf(body.get("topic"));
        String submission = String.valueOf(body.get("submissionJson"));
        Long assessmentId = body.get("assessmentId") == null ? null : Long.valueOf(String.valueOf(body.get("assessmentId")));

        JsonNode result = ai.evaluateAssessment(topic, submission);

        // store evaluation result
        var user = userService.getByEmail(auth.getName());
        AssessmentRecord ar = new AssessmentRecord();
        if (assessmentId != null) {
            ar = assessmentRepo.findById(assessmentId).orElse(new AssessmentRecord());
        }
        ar.setUser(user);
        ar.setTopic(topic);
        ar.setEvaluationJson(result.toString());
        ar.setQuestionCount(result.path("outOf").asInt(result.path("evaluation").size()));
        int score = result.path("score").asInt(0);
        ar.setScore(score);
        boolean passed = false;
        // passing criteria: 7/10 => 70%
        int outOf = ar.getQuestionCount();
        double pct = outOf == 0 ? 0.0 : (100.0 * score / outOf);
        if (outOf >= 10) passed = (score >= 7);
        else passed = (pct >= 70.0);

        ar.setPassed(passed);
        assessmentRepo.save(ar);

        return ResponseEntity.ok(result);
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

    // ---------------- Chat endpoints (UPDATED) ----------------

    /**
     * Create or append to a chat session and get AI reply.
     *
     * Accepts body shapes:
     *  - { "messages": [ { "role":"user", "content":"..." }, ... ], "sessionId": 12 }
     *  - { "message": { "role":"user", "content":"..." }, "sessionId": 12 }
     *
     * If sessionId is missing, a new session is created for the authenticated user.
     *
     * Behavior:
     *  - Persist only new user messages (avoid duplicating previously saved messages).
     *  - Build conversation history from DB (all messages for session ordered by createdAt).
     *  - Send history to AI, save assistant reply, return sessionId + replyMarkdown + replyHtml.
     */
    @PostMapping("/chat")
    @Transactional
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body, Authentication auth) {
        // resolve user
        var user = userService.getByEmail(auth.getName());

        // Accept either "messages" (array) or single "message"
        @SuppressWarnings("unchecked")
        List<Map<String, String>> incomingMessages = (List<Map<String, String>>) body.get("messages");
        if (incomingMessages == null) {
            incomingMessages = new ArrayList<>();
            @SuppressWarnings("unchecked")
            Map<String, String> single = (Map<String, String>) body.get("message");
            if (single != null) incomingMessages.add(single);
        }

        Long sessionId = body.get("sessionId") == null ? null : Long.valueOf(String.valueOf(body.get("sessionId")));
        ChatSession session = null;
        if (sessionId != null) {
            session = chatSessionRepo.findById(sessionId).orElse(null);
        }

        if (session == null) {
            session = new ChatSession();
            session.setUser(user);
            // create title based on first incoming user content if available
            String title = "Chat - " + Instant.now().toString().substring(0, 10);
            if (!incomingMessages.isEmpty()) {
                Optional<String> firstUser = incomingMessages.stream()
                        .filter(m -> "user".equalsIgnoreCase(m.getOrDefault("role", "user")))
                        .map(m -> m.getOrDefault("content", ""))
                        .filter(s -> !s.isBlank())
                        .findFirst();
                if (firstUser.isPresent()) {
                    String t = firstUser.get().trim();
                    if (t.length() > 40) t = t.substring(0, 40) + "...";
                    title = t;
                }
            }
            session.setTitle(title);
            chatSessionRepo.save(session); // persist to get id
        }

        // Load current saved messages for this session
        List<ChatMessage> savedMessages = chatMessageRepo.findBySessionIdOrderByCreatedAtAsc(session.getId());

        // Persist only new incoming user messages (avoid duplicates).
        // Strategy: consider a message new if its content doesn't exactly match the last saved message with same role.
        for (Map<String, String> m : incomingMessages) {
            String role = m.getOrDefault("role", "user");
            String content = m.getOrDefault("content", "");
            if (content == null || content.isBlank()) continue;

            boolean shouldSave = true;
            // find last saved message of same role
            for (int i = savedMessages.size() - 1; i >= 0; i--) {
                ChatMessage sm = savedMessages.get(i);
                if (sm.getRole() != null && sm.getRole().equalsIgnoreCase(role)) {
                    if (sm.getContent() != null && sm.getContent().equals(content)) {
                        shouldSave = false; // duplicate
                    }
                    break;
                }
            }
            if (shouldSave) {
                ChatMessage cm = new ChatMessage();
                cm.setRole(role);
                cm.setContent(content);
                cm.setSession(session);
                chatMessageRepo.save(cm);
                // maintain in-memory list
                savedMessages.add(cm);
                session.addMessage(cm);
            }
        }

        // Build messages list (role/content) to send to AI from DB history (ensures AI sees full context)
        List<Map<String, String>> historyForAi = savedMessages.stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .map(cm -> Map.of("role", cm.getRole(), "content", cm.getContent()))
                .collect(Collectors.toList());

        // Ask AI for a reply
        String replyMarkdown = ai.chatTutor(historyForAi);

        // Save assistant reply
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(replyMarkdown);
        assistantMsg.setSession(session);
        chatMessageRepo.save(assistantMsg);
        session.addMessage(assistantMsg);

        // Save session (update title maybe)
        chatSessionRepo.save(session);

        // Render HTML using CommonMark (for convenience)
        Node document = mdParser.parse(replyMarkdown == null ? "" : replyMarkdown);
        String html = htmlRenderer.render(document);

        Map<String, Object> resp = new HashMap<>();
        resp.put("sessionId", session.getId());
        resp.put("replyMarkdown", replyMarkdown);
        resp.put("replyHtml", html);

        return ResponseEntity.ok(resp);
    }

    /**
     * List chat sessions for current user (most recent first).
     * Returns minimal preview fields so UI can render sessions list.
     */
    @GetMapping("/sessions")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> listSessions(Authentication auth) {
        var user = userService.getByEmail(auth.getName());
        List<ChatSession> sessions = chatSessionRepo.findByUserIdOrderByCreatedAtDesc(user.getId());
        List<Map<String, Object>> out = sessions.stream().map(s -> {
            // try to get last message preview
            List<ChatMessage> messages = chatMessageRepo.findBySessionIdOrderByCreatedAtAsc(s.getId());
            String preview = "";
            if (!messages.isEmpty()) {
                ChatMessage last = messages.get(messages.size() - 1);
                String txt = last.getContent() == null ? "" : last.getContent();
                preview = txt.length() > 120 ? txt.substring(0, 120) + "..." : txt;
            }
            return Map.<String, Object>of(
                    "id", s.getId(),
                    "title", s.getTitle(),
                    "createdAt", s.getCreatedAt(),
                    "lastPreview", preview
            );
        }).collect(Collectors.toList());
        return ResponseEntity.ok(out);
    }

    /**
     * Get a single chat session including ordered messages.
     */
    @GetMapping("/sessions/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable Long id, Authentication auth) {
        var user = userService.getByEmail(auth.getName());
        ChatSession session = chatSessionRepo.findById(id).orElseThrow(() -> new RuntimeException("Session not found"));
        if (!session.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized to view this session"));
        }
        List<ChatMessage> messages = chatMessageRepo.findBySessionIdOrderByCreatedAtAsc(session.getId());
        List<Map<String, Object>> msgs = messages.stream().map(m -> Map.<String, Object>of(
                "id", m.getId(),
                "role", m.getRole(),
                "content", m.getContent(),
                "createdAt", m.getCreatedAt()
        )).collect(Collectors.toList());

        Map<String, Object> out = new HashMap<>();
        out.put("session", Map.of("id", session.getId(), "title", session.getTitle(), "createdAt", session.getCreatedAt()));
        out.put("messages", msgs);
        return ResponseEntity.ok(out);
    }

    /**
     * Rename a chat session (only owner can rename).
     * Body: { "title": "New title" }
     */
    @PutMapping("/sessions/{id}")
    @Transactional
    public ResponseEntity<?> renameSession(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        var user = userService.getByEmail(auth.getName());
        ChatSession session = chatSessionRepo.findById(id).orElseThrow(() -> new RuntimeException("Session not found"));
        if (!session.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized to rename this session"));
        }
        String newTitle = body.getOrDefault("title", "").trim();
        if (newTitle.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title cannot be empty"));
        }
        session.setTitle(newTitle);
        chatSessionRepo.save(session);
        return ResponseEntity.ok(Map.of("id", session.getId(), "title", session.getTitle(), "createdAt", session.getCreatedAt()));
    }

    /**
     * Delete a chat session (and its messages). Only owner can delete.
     */
    @DeleteMapping("/sessions/{id}")
    @Transactional
    public ResponseEntity<?> deleteSession(@PathVariable Long id, Authentication auth) {
        var user = userService.getByEmail(auth.getName());
        ChatSession session = chatSessionRepo.findById(id).orElseThrow(() -> new RuntimeException("Session not found"));
        if (!session.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized to delete this session"));
        }
        // Cascade and orphanRemoval on ChatSession.messages ensures messages are removed.
        chatSessionRepo.delete(session);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    // ---------------- other endpoints unchanged ----------------

    @PostMapping("/skill-gap")
    public ResponseEntity<Map<String, Object>> skillGap(@RequestBody Map<String,String> body, Authentication auth) {
        String resumeText = body.getOrDefault("resume", "");
        String role = body.getOrDefault("targetRole", "Software Engineer");
        JsonNode res = ai.analyzeSkillGap(resumeText, role);

        var user = userService.getByEmail(auth.getName());
        Recommendation r = new Recommendation();
        r.setUser(user);
        r.setTargetRole(role);
        r.setContentJson(res.toString());
        recommendationRepo.save(r);

        return ResponseEntity.ok(Map.of("result", res, "recommendationId", r.getId()));
    }

    @PostMapping("/mock-interview")
    public ResponseEntity<Map<String, Object>> mockInterview(@RequestBody Map<String, Object> body, Authentication auth) {
        String role = String.valueOf(body.getOrDefault("role", "Software Engineer"));
        int rounds = Integer.parseInt(String.valueOf(body.getOrDefault("rounds", "5")));
        JsonNode res = ai.generateMockInterview(role, rounds);

        var user = userService.getByEmail(auth.getName());
        MockInterview mi = new MockInterview();
        mi.setUser(user);
        mi.setRoleName(role);
        mi.setContentJson(res.toString());
        mockInterviewRepo.save(mi);

        return ResponseEntity.ok(Map.of("result", res, "mockInterviewId", mi.getId()));
    }

    @PostMapping("/flashcards")
    public ResponseEntity<Map<String,Object>> flashcards(@RequestBody Map<String, Object> body, Authentication auth) {
        String topic = String.valueOf(body.getOrDefault("topic", ""));
        int count = Integer.parseInt(String.valueOf(body.getOrDefault("count", "10")));
        JsonNode res = ai.generateFlashcards(topic, count);

        var user = userService.getByEmail(auth.getName());
        FlashcardCollection fc = new FlashcardCollection();
        fc.setUser(user);
        fc.setTopic(topic);
        fc.setTitle("Flashcards: " + topic);
        fc.setContentJson(res.toString());
        flashcardRepo.save(fc);

        return ResponseEntity.ok(Map.of("result", res, "flashcardCollectionId", fc.getId()));
    }

    @PostMapping("/coding-exercise")
    public ResponseEntity<JsonNode> codingExercise(@RequestBody Map<String, String> body) {
        String topic = body.getOrDefault("topic", "");
        return ResponseEntity.ok(ai.generateCodingExercise(topic));
    }
}
