package com.jarvis.discord;

import com.jarvis.ai.SummaryProvider;
import com.jarvis.board.BoardTask;
import com.jarvis.board.TaskPriority;
import com.jarvis.board.TaskService;
import com.jarvis.board.TaskStatus;
import com.jarvis.config.DiscordProperties;
import com.jarvis.rag.RagService;
import java.util.Locale;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DiscordMessageListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(DiscordMessageListener.class);
    private static final int DISCORD_MESSAGE_LIMIT = 1_900;

    private final DiscordProperties properties;
    private final TaskService taskService;
    private final SummaryProvider summaryProvider;
    private final RagService ragService;

    public DiscordMessageListener(
            DiscordProperties properties,
            TaskService taskService,
            SummaryProvider summaryProvider,
            RagService ragService) {
        this.properties = properties;
        this.taskService = taskService;
        this.summaryProvider = summaryProvider;
        this.ragService = ragService;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        Message message = event.getMessage();
        if (message.getAuthor().isBot()
                || !properties.allowedUserIds().contains(event.getAuthor().getId())
                || !event.getChannel().getId().equals(properties.commandChannelId())) {
            return;
        }
        String content = message.getContentRaw().trim();
        if (!content.startsWith("!")) {
            return;
        }
        try {
            String reply = handleCommand(content);
            if (reply != null) {
                event.getChannel().sendMessage(limit(reply)).queue();
            }
        } catch (RuntimeException exception) {
            log.warn("Échec du traitement d'une commande Discord: {}", exception.getMessage());
            event.getChannel().sendMessage("La commande a échoué: " + limit(exception.getMessage())).queue();
        }
    }

    private String handleCommand(String command) {
        if (command.equalsIgnoreCase("!help")) {
            return """
                    Commandes Jarvis:
                    `!task add <titre>` — créer une tâche
                    `!task list` — lister les tâches
                    `!task done <id>` — terminer une tâche
                    `!ask <question>` — interroger Ollama
                    `!askdoc <question>` — interroger les documents indexés""";
        }
        if (command.regionMatches(true, 0, "!task ", 0, 6)) {
            return handleTaskCommand(command.substring(6).trim());
        }
        if (command.regionMatches(true, 0, "!ask ", 0, 5)) {
            String prompt = command.substring(5).trim();
            if (prompt.isBlank()) {
                return "Précisez une question après `!ask`.";
            }
            return summaryProvider.generate(prompt);
        }
        if (command.regionMatches(true, 0, "!askdoc ", 0, 8)) {
            String question = command.substring(8).trim();
            if (question.isBlank()) {
                return "Précisez une question après `!askdoc`.";
            }
            return ragService.query(question);
        }
        return null;
    }

    private String handleTaskCommand(String command) {
        String normalized = command.toLowerCase(Locale.ROOT);
        if (normalized.equals("list")) {
            return taskService.findAll().stream()
                    .map(this::formatTask)
                    .reduce((first, second) -> first + "\n" + second)
                    .orElse("Aucune tâche.");
        }
        if (normalized.startsWith("add ")) {
            String title = command.substring(4).trim();
            if (title.isBlank()) {
                return "Le titre de la tâche est obligatoire.";
            }
            BoardTask task = taskService.create(title, null, TaskPriority.MEDIUM);
            return "Tâche créée (#" + task.getId() + "): " + task.getTitle();
        }
        if (normalized.startsWith("done ")) {
            long id = Long.parseLong(command.substring(5).trim());
            BoardTask task = taskService.updateStatus(id, TaskStatus.DONE);
            return "Tâche #" + task.getId() + " terminée: " + task.getTitle();
        }
        return "Commande inconnue. Utilisez `!help`.";
    }

    private String formatTask(BoardTask task) {
        return "#%d [%s / %s] %s".formatted(
                task.getId(), task.getStatus(), task.getPriority(), task.getTitle());
    }

    private String limit(String text) {
        if (text == null) {
            return "Une erreur inattendue est survenue.";
        }
        return text.length() <= DISCORD_MESSAGE_LIMIT
                ? text
                : text.substring(0, DISCORD_MESSAGE_LIMIT - 3) + "...";
    }
}
