package com.jarvis.board;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BoardTask create(@Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(request.title(), request.description(), request.priority());
    }

    @GetMapping
    public List<BoardTask> list() {
        return taskService.findAll();
    }

    @PatchMapping("/{id}/status")
    public BoardTask updateStatus(@PathVariable long id, @Valid @RequestBody UpdateTaskStatusRequest request) {
        return taskService.updateStatus(id, request.status());
    }

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

    public record UpdateTaskStatusRequest(TaskStatus status) {
        public UpdateTaskStatusRequest {
            if (status == null) {
                throw new IllegalArgumentException("Le statut est obligatoire.");
            }
        }
    }
}
