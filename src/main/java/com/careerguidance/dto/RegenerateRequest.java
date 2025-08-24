package com.careerguidance.dto;

public class RegenerateRequest {
    private int fromIndex;
    private String reason; // "procrastination" | "failure" | etc.

    public int getFromIndex() { return fromIndex; }
    public String getReason() { return reason; }
    public void setFromIndex(int fromIndex) { this.fromIndex = fromIndex; }
    public void setReason(String reason) { this.reason = reason; }
}