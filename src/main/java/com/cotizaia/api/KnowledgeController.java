package com.cotizaia.api;

import com.cotizaia.agent.knowledge.IngestionResult;
import com.cotizaia.agent.knowledge.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/knowledge")
@Tag(name = "Knowledge")
public class KnowledgeController {

    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @PostMapping(value = "/documents", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Ingest a past proposal PDF into agency memory")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeUploadResponse upload(@RequestParam("file") MultipartFile file, CurrentUser user) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            name = "document.pdf";
        }
        IngestionResult result = service.upload(user.agencyId(), name, bytes);
        return KnowledgeUploadResponse.from(name, result);
    }

    @GetMapping("/documents")
    @Operation(summary = "List knowledge documents")
    public List<KnowledgeDocumentListResponse> list(CurrentUser user) {
        return service.list(user.agencyId()).stream()
                .map(document -> KnowledgeDocumentListResponse.from(document,
                        (int) service.itemCount(document.getId()),
                        (int) service.passageCount(document.getId())))
                .toList();
    }

    @DeleteMapping("/documents/{id}")
    @Operation(summary = "Delete a knowledge document")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, CurrentUser user) {
        service.delete(user.agencyId(), id);
    }

    @GetMapping("/search")
    @Operation(summary = "Search agency memory")
    public List<KnowledgeSearchResponse> search(@RequestParam("q") String q,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "k", required = false) Integer k, CurrentUser user) {
        return service.search(user.agencyId(), q, type, k).stream()
                .map(KnowledgeSearchResponse::from).toList();
    }
}
