package com.cotizaia.ingest;

import java.util.List;

/**
 * Text extracted from an uploaded RFP PDF. Pages with no extractable text
 * (e.g. scanned images) are reported by 1-based number instead of failing the
 * upload, so the operator knows which pages need manual review.
 */
public record PdfExtraction(String text, int pages, boolean truncated, List<Integer> unreadablePages) {
}
