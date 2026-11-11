package com.jarvis.board.bean;

import com.jarvis.board.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        TaskPriority priority) {
    public CreateTaskRequest {
        if (priority == null) {
            priority = TaskPriority.MEDIUM;
        }
    }
}
