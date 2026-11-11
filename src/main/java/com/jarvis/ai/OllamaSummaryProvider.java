package com.jarvis.ai;

import org.springframework.stereotype.Service;

@Service
public class OllamaSummaryProvider implements SummaryProvider {
    private static final String SYSTEM_PROMPT = """
            Tu es Jarvis, un assistant personnel local. Réponds en français, de façon factuelle,
            concise et utile. Signale clairement toute incertitude et n'invente pas de faits.
            """;

    private final OllamaClient ollamaClient;

    public OllamaSummaryProvider(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    @Override
    public String generate(String prompt) {
        return ollamaClient.generate(SYSTEM_PROMPT + "\n\n" + prompt);
    }
}
