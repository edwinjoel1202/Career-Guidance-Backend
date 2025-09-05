package com.careerguidance.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "recommendations")
public class Recommendation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String targetRole;

    @Lob
    @Column(columnDefinition = "text")
    private String contentJson;

    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // getters/setters
    public Long getId() { return id; }
    public String getTargetRole() { return targetRole; }
    public String getContentJson() { return contentJson; }
    public Instant getCreatedAt() { return createdAt; }
    public User getUser() { return user; }
    public void setId(Long id) { this.id = id; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUser(User user) { this.user = user; }
}
