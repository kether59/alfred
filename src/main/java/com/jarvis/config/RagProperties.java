package com.jarvis.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jarvis.rag")
public record RagProperties(Path documentsDirectory, int chunkSize, int chunkOverlap) {
}
