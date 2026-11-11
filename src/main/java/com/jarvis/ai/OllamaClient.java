package com.jarvis.ai;

import com.jarvis.config.OllamaProperties;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class OllamaClient {
    private final WebClient webClient;
    private final OllamaProperties properties;

    public OllamaClient(WebClient ollamaWebClient, OllamaProperties properties) {
        this.webClient = ollamaWebClient;
        this.properties = properties;
    }

    public String generate(String prompt) {
        try {
            OllamaResponse response = webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "model", properties.model(),
                            "prompt", prompt,
                            "stream", false))
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .block(properties.timeout());
            if (response == null || response.response() == null || response.response().isBlank()) {
                throw new OllamaUnavailableException("Ollama a retourné une réponse vide.");
            }
            return response.response().trim();
        } catch (WebClientResponseException | WebClientRequestException exception) {
            throw new OllamaUnavailableException("Impossible de joindre Ollama: " + exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            if (hasTimeoutCause(exception)) {
                throw new OllamaUnavailableException("Ollama n'a pas répondu avant le délai configuré.", exception);
            }
            throw exception;
        }
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private record OllamaResponse(String response) {
    }
}
