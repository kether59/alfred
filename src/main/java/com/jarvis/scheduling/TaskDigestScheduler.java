package com.jarvis.scheduling;

import com.jarvis.board.TaskService;
import com.jarvis.board.TaskStatus;
import com.jarvis.config.DiscordProperties;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "jarvis.discord", name = "enabled", havingValue = "true")
public class TaskDigestScheduler {
    private static final Logger log = LoggerFactory.getLogger(TaskDigestScheduler.class);
    private static final int DISCORD_MESSAGE_LIMIT = 1_900;

    private final ObjectProvider<JDA> jdaProvider;
    private final DiscordProperties discordProperties;
    private final TaskService taskService;

    public TaskDigestScheduler(
            ObjectProvider<JDA> jdaProvider,
            DiscordProperties discordProperties,
            TaskService taskService) {
        this.jdaProvider = jdaProvider;
        this.discordProperties = discordProperties;
        this.taskService = taskService;
    }

    @Scheduled(cron = "${jarvis.scheduling.digest-cron:0 0 8 * * *}")
    public void sendDailyDigest() {
        if (discordProperties.digestChannelId() == null || discordProperties.digestChannelId().isBlank()) {
            return;
        }
        Optional<JDA> jda = Optional.ofNullable(jdaProvider.getIfAvailable());
        if (jda.isEmpty()) {
            return;
        }
        TextChannel channel = jda.get().getTextChannelById(discordProperties.digestChannelId());
        if (channel == null) {
            log.warn("Salon Discord de digest introuvable: {}", discordProperties.digestChannelId());
            return;
        }
        String pendingTasks = taskService.findAll().stream()
                .filter(task -> task.getStatus() != TaskStatus.DONE)
                .map(task -> "#%d [%s] %s".formatted(task.getId(), task.getPriority(), task.getTitle()))
                .reduce((first, second) -> first + "\n" + second)
                .orElse("Aucune tâche en attente.");
        String message = "Digest Jarvis — tâches en cours:\n" + pendingTasks;
        if (message.length() > DISCORD_MESSAGE_LIMIT) {
            message = message.substring(0, DISCORD_MESSAGE_LIMIT - 3) + "...";
        }
        channel.sendMessage(message).queue(
                ignored -> { },
                exception -> log.error("Impossible d'envoyer le digest Discord.", exception));
    }
}
