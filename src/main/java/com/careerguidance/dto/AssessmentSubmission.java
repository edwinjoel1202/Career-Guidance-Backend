package com.careerguidance.dto;

import java.util.List;

public class AssessmentSubmission {

    public static class Answer {
        private String question;
        private String correctAnswer;
        private String userAnswer;

        public String getQuestion() { return question; }
        public String getCorrectAnswer() { return correctAnswer; }
        public String getUserAnswer() { return userAnswer; }
        public void setQuestion(String question) { this.question = question; }
        public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
        public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
    }

    private String topic;
    private List<Answer> answers;

    public String getTopic() { return topic; }
    public List<Answer> getAnswers() { return answers; }
    public void setTopic(String topic) { this.topic = topic; }
    public void setAnswers(List<Answer> answers) { this.answers = answers; }
}
