package com.careerguidance.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "assessments")
public class AssessmentRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String topic;
    private String learningPathId; // optional; store path id as string when needed
    private Integer topicIndex; // which item index

    private int questionCount;
    private int score; // score out of questionCount
    private boolean passed;

    @Lob
    @Column(columnDefinition = "text")
    private String assessmentJson; // questions JSON

    @Lob
    @Column(columnDefinition = "text")
    private String evaluationJson; // evaluation JSON returned by AI

    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // getters/setters
    public Long getId() { return id; }
    public String getTopic() { return topic; }
    public String getLearningPathId() { return learningPathId; }
    public Integer getTopicIndex() { return topicIndex; }
    public int getQuestionCount() { return questionCount; }
    public int getScore() { return score; }
    public boolean isPassed() { return passed; }
    public String getAssessmentJson() { return assessmentJson; }
    public String getEvaluationJson() { return evaluationJson; }
    public Instant getCreatedAt() { return createdAt; }
    public User getUser() { return user; }
    public void setId(Long id) { this.id = id; }
    public void setTopic(String topic) { this.topic = topic; }
    public void setLearningPathId(String learningPathId) { this.learningPathId = learningPathId; }
    public void setTopicIndex(Integer topicIndex) { this.topicIndex = topicIndex; }
    public void setQuestionCount(int questionCount) { this.questionCount = questionCount; }
    public void setScore(int score) { this.score = score; }
    public void setPassed(boolean passed) { this.passed = passed; }
    public void setAssessmentJson(String assessmentJson) { this.assessmentJson = assessmentJson; }
    public void setEvaluationJson(String evaluationJson) { this.evaluationJson = evaluationJson; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUser(User user) { this.user = user; }
}
