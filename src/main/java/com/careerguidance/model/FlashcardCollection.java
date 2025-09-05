package com.careerguidance.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "flashcard_collections")
public class FlashcardCollection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String topic;
    private String title;

    @Lob
    @Column(columnDefinition = "text")
    private String contentJson; // store cards as JSON array

    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // getters/setters
    public Long getId() { return id; }
    public String getTopic() { return topic; }
    public String getTitle() { return title; }
    public String getContentJson() { return contentJson; }
    public Instant getCreatedAt() { return createdAt; }
    public User getUser() { return user; }
    public void setId(Long id) { this.id = id; }
    public void setTopic(String topic) { this.topic = topic; }
    public void setTitle(String title) { this.title = title; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUser(User user) { this.user = user; }
}
