package com.cotizaia.ingest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Reads an uploaded RFP PDF into brief text. Limits are enforced before and
 * during parsing (size first, so junk bytes never reach the parser; then page
 * count; then text length) to keep oversized uploads away from the agent.
 */
@Component
public class PdfBriefReader {

    public static final int MAX_BYTES = 10 * 1024 * 1024;

    public static final int MAX_PAGES = 30;

    public static final int MAX_TEXT_CHARS = 20_000;

    /**
     * Extracts one string per page from raw PDF bytes. A page with no
     * extractable text becomes an empty string. Validation and limits match
     * {@link #read}.
     */
    public List<String> readPages(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        if (bytes.length > MAX_BYTES) {
            throw new UploadTooLargeException("File exceeds 10 MB");
        }
        String header = new String(bytes, 0, Math.min(bytes.length, 5), StandardCharsets.US_ASCII);
        if (!header.startsWith("%PDF-")) {
            throw new IllegalArgumentException("File is not a PDF");
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("Encrypted PDF files are not supported");
            }
            int pages = document.getNumberOfPages();
            if (pages > MAX_PAGES) {
                throw new IllegalArgumentException(
                        "PDF has " + pages + " pages, maximum is " + MAX_PAGES);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> result = new ArrayList<>();
            List<String> nonBlank = new ArrayList<>();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                if (text == null || text.isBlank()) {
                    result.add("");
                } else {
                    String stripped = text.strip();
                    result.add(stripped);
                    nonBlank.add(stripped);
                }
            }
            if (nonBlank.isEmpty()) {
                throw new IllegalArgumentException("PDF has no extractable text");
            }
            return List.copyOf(result);
        } catch (IOException e) {
            throw new IllegalArgumentException("File is not a readable PDF", e);
        }
    }

    /**
     * Extracts the brief text from raw PDF bytes.
     *
     * @throws IllegalArgumentException when the bytes are empty, are not a
     *         readable PDF, or hold no extractable text at all.
     * @throws UploadTooLargeException when the file exceeds {@link #MAX_BYTES}.
     */
    public PdfExtraction read(byte[] bytes) {
        List<String> pages = readPages(bytes);
        List<String> texts = pages.stream().filter(s -> !s.isBlank()).toList();
        List<Integer> unreadablePages = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).isBlank()) {
                unreadablePages.add(i + 1);
            }
        }
        String joined = String.join("\n\n", texts);
        boolean truncated = joined.length() > MAX_TEXT_CHARS;
        String text = truncated ? joined.substring(0, MAX_TEXT_CHARS) : joined;
        return new PdfExtraction(text, pages.size(), truncated, List.copyOf(unreadablePages));
    }
}