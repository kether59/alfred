package com.jarvis.board;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BoardPageController {
    private final TaskService taskService;

    public BoardPageController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/")
    public String board(Model model) {
        model.addAttribute("tasks", taskService.findAll());
        model.addAttribute("statuses", TaskStatus.values());
        model.addAttribute("priorities", TaskPriority.values());
        return "board";
    }

    @PostMapping("/tasks")
    public String createTask(
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "MEDIUM") TaskPriority priority,
            RedirectAttributes redirectAttributes) {
        if (title == null || title.isBlank() || title.length() > 200
                || description != null && description.length() > 2000) {
            redirectAttributes.addFlashAttribute("error", "Le titre ou la description dépasse la longueur autorisée.");
            return "redirect:/";
        }
        taskService.create(title, description, priority);
        return "redirect:/";
    }

    @PostMapping("/tasks/status")
    public String updateStatus(
            @RequestParam long id,
            @RequestParam TaskStatus status) {
        taskService.updateStatus(id, status);
        return "redirect:/";
    }
}
