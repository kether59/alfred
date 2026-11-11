package com.jarvis.discord;

import com.jarvis.config.DiscordProperties;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "jarvis.discord", name = "enabled", havingValue = "true")
public class DiscordBotConfiguration {

    @Bean(destroyMethod = "shutdown")
    JDA jda(DiscordProperties properties, DiscordMessageListener listener) {
        if (properties.token() == null || properties.token().isBlank()) {
            throw new IllegalStateException("DISCORD_BOT_TOKEN est obligatoire lorsque le bot Discord est activé.");
        }
        if (properties.allowedUserIds().isEmpty()) {
            throw new IllegalStateException("Configurez au moins un ID dans DISCORD_ALLOWED_USER_IDS.");
        }
        if (properties.commandChannelId() == null || properties.commandChannelId().isBlank()) {
            throw new IllegalStateException("Configurez le salon dédié dans DISCORD_CHANNEL_ID.");
        }
        return JDABuilder.createDefault(properties.token())
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(listener)
                .build();
    }
}
