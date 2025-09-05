package com.careerguidance.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String role; // "user" | "assistant" | "system"
    @Lob
    @Column(columnDefinition = "text")
    private String content; // raw markdown preserved

    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ChatSession session;

    // getters/setters
    public Long getId() { return id; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public ChatSession getSession() { return session; }
    public void setId(Long id) { this.id = id; }
    public void setRole(String role) { this.role = role; }
    public void setContent(String content) { this.content = content; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setSession(ChatSession session) { this.session = session; }
}
