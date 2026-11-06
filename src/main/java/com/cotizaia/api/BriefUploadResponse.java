package com.cotizaia.api;

import java.util.List;

/**
 * A PDF-channel brief plus the upload metadata the operator needs: which file
 * it came from, how many pages it had, and which pages held no text.
 */
public record BriefUploadResponse(BriefResponse brief, String fileName, int pages, boolean truncated,
        List<Integer> unreadablePages) {
}
