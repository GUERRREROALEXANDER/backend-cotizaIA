package com.cotizaia.api;

import com.cotizaia.document.DocumentType;
import com.cotizaia.domain.ProposalDocument;
import java.time.Instant;

/** Public projection without persistence internals. */
public record DocumentResponse(DocumentType type, String fileName, long sizeBytes,
        Instant generatedAt, String downloadUrl) {

    public static DocumentResponse from(ProposalDocument document) {
        return new DocumentResponse(document.getType(), document.getFileName(), document.getSizeBytes(),
                document.getGeneratedAt(), "/api/proposals/" + document.getProposal().getId()
                        + "/documents/" + document.getType());
    }
}
