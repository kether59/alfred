package com.jarvis.rag;

import com.jarvis.ai.SummaryProvider;
import com.jarvis.common.UnsupportedDocumentException;
import com.jarvis.config.RagProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RagService {
    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int MAX_CONTEXT_CHARS = 8_000;

    private final SummaryProvider summaryProvider;
    private final RagProperties properties;
    private final Map<String, List<String>> indexedDocuments = new ConcurrentHashMap<>();

    public RagService(SummaryProvider summaryProvider, RagProperties properties) {
        this.summaryProvider = summaryProvider;
        this.properties = properties;
        if (properties.chunkSize() < 200 || properties.chunkOverlap() < 0
                || properties.chunkOverlap() >= properties.chunkSize()) {
            throw new IllegalArgumentException("La taille des chunks doit être >= 200 et supérieure à l'overlap.");
        }
    }

    @PostConstruct
    void initializeDocuments() {
        try {
            Files.createDirectories(properties.documentsDirectory());
            try (Stream<Path> files = Files.list(properties.documentsDirectory())) {
                files.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).forEach(path -> {
                    if (isSupported(path)) {
                        try {
                            index(path);
                        } catch (IOException | UnsupportedDocumentException exception) {
                            log.warn("Impossible d'indexer le document {}: {}", path.getFileName(), exception.getMessage());
                        }
                    }
                });
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible de lire le dossier documentaire "
                    + properties.documentsDirectory(), exception);
        }
    }

    public String upload(MultipartFile upload) {
        if (upload.isEmpty()) {
            throw new IllegalArgumentException("Le document est vide.");
        }
        String originalName = upload.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("Le nom du document est absent.");
        }
        Path uploadedPath = Path.of(originalName.replace('\\', '/')).getFileName();
        if (uploadedPath == null) {
            throw new IllegalArgumentException("Nom de fichier invalide.");
        }
        String safeName = uploadedPath.toString();
        String extension = extensionOf(safeName);
        if (!List.of("pdf", "txt", "md", "markdown").contains(extension)) {
            throw new UnsupportedDocumentException("Formats acceptés: PDF, TXT et Markdown.");
        }

        String storedName = UUID.randomUUID() + "-" + safeName;
        Path destination = properties.documentsDirectory().resolve(storedName).normalize();
        if (!destination.getParent().equals(properties.documentsDirectory().normalize())) {
            throw new IllegalArgumentException("Nom de fichier invalide.");
        }
        try {
            Files.createDirectories(properties.documentsDirectory());
            try (var input = upload.getInputStream()) {
                Files.copy(input, destination);
            }
            index(destination);
            return storedName;
        } catch (IOException | UnsupportedDocumentException exception) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            if (exception instanceof UnsupportedDocumentException unsupportedDocumentException) {
                throw unsupportedDocumentException;
            }
            throw new UnsupportedDocumentException("Impossible de stocker ou lire le document.", exception);
        }
    }

    public String query(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("La question ne peut pas être vide.");
        }
        List<String> context = indexedDocuments.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(chunk -> new ScoredChunk(entry.getKey(), chunk, score(question, chunk))))
                .filter(chunk -> chunk.score() > 0)
                .sorted(Comparator.comparingInt(ScoredChunk::score).reversed())
                .limit(5)
                .map(chunk -> "[" + chunk.source() + "] " + chunk.text())
                .toList();
        if (context.isEmpty()) {
            throw new IllegalArgumentException("Aucun passage pertinent n'a été trouvé dans les documents indexés.");
        }
        String combinedContext = String.join("\n\n", context);
        if (combinedContext.length() > MAX_CONTEXT_CHARS) {
            combinedContext = combinedContext.substring(0, MAX_CONTEXT_CHARS);
        }
        return summaryProvider.generate("""
                Réponds à la question en t'appuyant uniquement sur les extraits ci-dessous.
                Si les extraits ne permettent pas de répondre, indique-le clairement.

                Extraits:
                %s

                Question: %s
                """.formatted(combinedContext, question));
    }

    public String summarize(String documentName) {
        List<String> chunks = requireDocument(documentName);
        String content = String.join("\n\n", chunks);
        if (content.length() > MAX_CONTEXT_CHARS) {
            content = content.substring(0, MAX_CONTEXT_CHARS);
        }
        return summaryProvider.generate("Résume les informations essentielles de ce document:\n\n" + content);
    }

    public String translate(String documentName, String targetLanguage) {
        if (targetLanguage == null || targetLanguage.isBlank()) {
            throw new IllegalArgumentException("La langue cible est obligatoire.");
        }
        List<String> chunks = requireDocument(documentName);
        String content = String.join("\n\n", chunks);
        if (content.length() > MAX_CONTEXT_CHARS) {
            content = content.substring(0, MAX_CONTEXT_CHARS);
        }
        return summaryProvider.generate("Traduis fidèlement le texte suivant en " + targetLanguage
                + ", sans ajouter d'information:\n\n" + content);
    }

    public List<String> listDocuments() {
        return indexedDocuments.keySet().stream().sorted().toList();
    }

    private List<String> requireDocument(String documentName) {
        List<String> chunks = indexedDocuments.get(documentName);
        if (chunks == null) {
            throw new IllegalArgumentException("Document non indexé: " + documentName);
        }
        return chunks;
    }

    private void index(Path path) throws IOException {
        String content = readText(path);
        if (content.isBlank()) {
            throw new UnsupportedDocumentException("Le document ne contient aucun texte lisible: " + path.getFileName());
        }
        indexedDocuments.put(path.getFileName().toString(), split(content));
        log.info("Document indexé: {}", path.getFileName());
    }

    private String readText(Path path) throws IOException {
        return switch (extensionOf(path.getFileName().toString())) {
            case "txt", "md", "markdown" -> Files.readString(path, StandardCharsets.UTF_8);
            case "pdf" -> {
                try (var document = Loader.loadPDF(path.toFile())) {
                    yield new PDFTextStripper().getText(document);
                }
            }
            default -> throw new UnsupportedDocumentException("Format non pris en charge.");
        };
    }

    private List<String> split(String content) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + properties.chunkSize(), content.length());
            if (end < content.length()) {
                int boundary = content.lastIndexOf(' ', end);
                if (boundary > start + properties.chunkSize() / 2) {
                    end = boundary;
                }
            }
            String chunk = content.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end == content.length()) {
                break;
            }
            start = Math.max(start + 1, end - properties.chunkOverlap());
        }
        return List.copyOf(chunks);
    }

    private int score(String question, String chunk) {
        String normalizedChunk = chunk.toLowerCase(Locale.ROOT);
        return List.of(question.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")).stream()
                .filter(token -> token.length() > 2)
                .mapToInt(token -> occurrences(normalizedChunk, token))
                .sum();
    }

    private int occurrences(String text, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static boolean isSupported(Path path) {
        return List.of("pdf", "txt", "md", "markdown").contains(extensionOf(path.getFileName().toString()));
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private record ScoredChunk(String source, String text, int score) {
    }
}
