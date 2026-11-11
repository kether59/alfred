package com.jarvis.rag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class RagController {
    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse upload(@RequestPart("file") MultipartFile file) {
        return new UploadResponse(ragService.upload(file));
    }

    @GetMapping
    public List<String> list() {
        return ragService.listDocuments();
    }

    @PostMapping("/query")
    public TextResponse query(@Valid @org.springframework.web.bind.annotation.RequestBody QueryRequest request) {
        return new TextResponse(ragService.query(request.question()));
    }

    @PostMapping("/{documentName}/summary")
    public TextResponse summarize(@PathVariable String documentName) {
        return new TextResponse(ragService.summarize(documentName));
    }

    @PostMapping("/{documentName}/translation")
    public TextResponse translate(@PathVariable String documentName, @RequestParam String targetLanguage) {
        return new TextResponse(ragService.translate(documentName, targetLanguage));
    }

    public record UploadResponse(String storedName) {
    }

    public record TextResponse(String text) {
    }

    public record QueryRequest(@NotBlank String question) {
    }
}
