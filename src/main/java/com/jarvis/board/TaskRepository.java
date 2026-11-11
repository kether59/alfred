package com.jarvis.board;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<BoardTask, Long> {
    List<BoardTask> findAllByOrderByCreatedAtDesc();
}
