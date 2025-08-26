package com.careerguidance.model;

import jakarta.persistence.*;

@Embeddable
public class PathItem {
    private String topic;
    private int duration;           // days
    private String startDate;       // ISO yyyy-MM-dd
    private String endDate;         // ISO yyyy-MM-dd
    private String status;
    // pending/completed/failed

    @Lob
    @Column(columnDefinition = "TEXT")
    private String assessmentResult;

    @Column(columnDefinition = "text")
    private String notes;// raw JSON string

    // getters & setters
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getTopic() { return topic; }
    public int getDuration() { return duration; }
    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }
    public String getStatus() { return status; }
    public String getAssessmentResult() { return assessmentResult; }
    public void setTopic(String topic) { this.topic = topic; }
    public void setDuration(int duration) { this.duration = duration; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public void setStatus(String status) { this.status = status; }
    public void setAssessmentResult(String assessmentResult) { this.assessmentResult = assessmentResult; }
}
