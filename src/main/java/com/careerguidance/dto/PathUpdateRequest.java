package com.careerguidance.dto;

import com.careerguidance.model.PathItem;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class PathUpdateRequest {
    @NotNull
    private List<PathItem> path;

    public List<PathItem> getPath() { return path; }
    public void setPath(List<PathItem> path) { this.path = path; }
}
