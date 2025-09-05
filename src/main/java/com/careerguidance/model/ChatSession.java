package com.careerguidance.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_sessions")
public class ChatSession {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title; // optional friendly title

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessage> messages = new ArrayList<>();

    // getters / setters
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public User getUser() { return user; }
    public Instant getCreatedAt() { return createdAt; }
    public List<ChatMessage> getMessages() { return messages; }
    public void setId(Long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setUser(User user) { this.user = user; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }

    public void addMessage(ChatMessage m) {
        messages.add(m);
        m.setSession(this);
    }
}
