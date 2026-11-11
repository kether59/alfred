package com.jarvis.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jarvis.ollama")
public record OllamaProperties(String baseUrl, String model, Duration timeout) {
}
