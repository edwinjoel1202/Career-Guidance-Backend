package com.careerguidance.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "learning_paths")
public class LearningPath {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String domain;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "path_items", joinColumns = @JoinColumn(name = "path_id"))
    private List<PathItem> path = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt = new Date();

    // getters & setters
    public Long getId() { return id; }
    public String getDomain() { return domain; }
    public List<PathItem> getPath() { return path; }
    public User getUser() { return user; }
    public Date getCreatedAt() { return createdAt; }
    public void setId(Long id) { this.id = id; }
    public void setDomain(String domain) { this.domain = domain; }
    public void setPath(List<PathItem> path) { this.path = path; }
    public void setUser(User user) { this.user = user; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
