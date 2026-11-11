package com.jarvis.config;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jarvis.discord")
public record DiscordProperties(
        boolean enabled,
        String token,
        Set<String> allowedUserIds,
        String commandChannelId,
        String digestChannelId) {
    public DiscordProperties {
        allowedUserIds = allowedUserIds == null
                ? Set.of()
                : allowedUserIds.stream()
                        .filter(id -> id != null && !id.isBlank())
                        .map(String::trim)
                        .collect(Collectors.toUnmodifiableSet());
        commandChannelId = commandChannelId == null ? "" : commandChannelId.trim();
        digestChannelId = digestChannelId == null ? "" : digestChannelId.trim();
    }
}
