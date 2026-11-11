package com.jarvis.board;

import com.jarvis.common.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaskService {
    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public BoardTask create(String title, String description, TaskPriority priority) {
        return taskRepository.save(new BoardTask(title.trim(), description, priority));
    }

    @Transactional(readOnly = true)
    public List<BoardTask> findAll() {
        return taskRepository.findAllByOrderByCreatedAtDesc();
    }

    public BoardTask updateStatus(long id, TaskStatus status) {
        BoardTask task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tâche " + id + " introuvable."));
        task.setStatus(status);
        return taskRepository.save(task);
    }
}
