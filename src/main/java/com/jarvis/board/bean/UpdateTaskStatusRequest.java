package com.jarvis.board.bean;


import com.jarvis.board.TaskStatus;

public record UpdateTaskStatusRequest(TaskStatus status) {
    public UpdateTaskStatusRequest {
        if (status == null) {
            throw new IllegalArgumentException("Le statut est obligatoire.");
        }
    }
}

