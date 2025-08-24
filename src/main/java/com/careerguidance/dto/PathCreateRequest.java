package com.careerguidance.dto;

import com.careerguidance.model.PathItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class PathCreateRequest {
    @NotBlank
    private String domain;

    @NotNull
    private List<PathItem> path;

    public String getDomain() { return domain; }
    public List<PathItem> getPath() { return path; }
    public void setDomain(String domain) { this.domain = domain; }
    public void setPath(List<PathItem> path) { this.path = path; }
}
