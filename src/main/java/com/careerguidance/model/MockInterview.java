package com.careerguidance.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mock_interviews")
public class MockInterview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String roleName;

    @Lob
    @Column(columnDefinition = "text")
    private String contentJson; // store Gemini JSON text as-is

    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // getters/setters
    public Long getId() { return id; }
    public String getRoleName() { return roleName; }
    public String getContentJson() { return contentJson; }
    public Instant getCreatedAt() { return createdAt; }
    public User getUser() { return user; }
    public void setId(Long id) { this.id = id; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUser(User user) { this.user = user; }
}
