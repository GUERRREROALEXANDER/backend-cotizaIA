package com.cotizaia.service;

import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefAttachment;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.ingest.PdfBriefReader;
import com.cotizaia.ingest.PdfExtraction;
import com.cotizaia.repository.BriefAttachmentRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns an uploaded RFP PDF into a PDF-channel brief while keeping the
 * original file. Ingest goes through {@link BriefService} so agency ownership
 * of the client is enforced exactly like typed briefs.
 */
@Service
public class BriefUploadService {

    static final int MAX_NOTE_CHARS = 1_000;

    static final int MAX_FILE_NAME_CHARS = 255;

    private final PdfBriefReader reader;

    private final BriefService briefService;

    private final BriefAttachmentRepository attachments;

    public BriefUploadService(PdfBriefReader reader, BriefService briefService,
            BriefAttachmentRepository attachments) {
        this.reader = reader;
        this.briefService = briefService;
        this.attachments = attachments;
    }

    /** One uploaded RFP and the brief plus file metadata it produced. */
    public record UploadResult(Brief brief, String fileName, int pages, boolean truncated,
            List<Integer> unreadablePages) {
    }

    /**
     * Extracts the brief text, ingests it on the PDF channel and stores the
     * original bytes for later download.
     *
     * @throws IllegalArgumentException when the note exceeds 1000 characters
     *         or the file is not a readable PDF with extractable text.
     */
    @Transactional
    public UploadResult upload(Long agencyId, Long clientId, String originalFileName, byte[] bytes,
            String note) {
        if (note != null && note.length() > MAX_NOTE_CHARS) {
            throw new IllegalArgumentException("Field note exceeds 1000 characters");
        }
        PdfExtraction extraction = reader.read(bytes);
        String fileName = sanitizeFileName(originalFileName);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", extraction.text());
        if (note != null && !note.isBlank()) {
            payload.put("note", note);
        }
        payload.put("fileName", fileName);
        payload.put("pages", extraction.pages());
        payload.put("truncated", extraction.truncated());
        payload.put("unreadablePages", extraction.unreadablePages());
        Brief brief = briefService.ingest(agencyId, BriefChannel.PDF, clientId, payload);
        attachments.save(new BriefAttachment(brief, fileName, "application/pdf", bytes, extraction.pages(),
                Instant.now()));
        return new UploadResult(brief, fileName, extraction.pages(), extraction.truncated(),
                extraction.unreadablePages());
    }

    /**
     * Loads the stored upload for a brief. Ownership is checked through the
     * brief first, so another agency's file reads as not found.
     *
     * @throws NoSuchElementException when the brief or its attachment is missing.
     */
    @Transactional(readOnly = true)
    public BriefAttachment attachmentFor(Long agencyId, Long briefId) {
        briefService.get(agencyId, briefId);
        return attachments.findByBriefId(briefId)
                .orElseThrow(() -> new NoSuchElementException("Attachment not found: " + briefId));
    }

    /**
     * Keeps only the last path segment (browsers may send a full client path)
     * within the column limit, defaulting blank names to {@code brief.pdf}.
     */
    static String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "brief.pdf";
        }
        String segment = originalFileName.replace('\\', '/');
        int slash = segment.lastIndexOf('/');
        if (slash >= 0) {
            segment = segment.substring(slash + 1);
        }
        segment = segment.strip();
        if (segment.isBlank()) {
            return "brief.pdf";
        }
        return segment.length() > MAX_FILE_NAME_CHARS
                ? segment.substring(0, MAX_FILE_NAME_CHARS)
                : segment;
    }
}
