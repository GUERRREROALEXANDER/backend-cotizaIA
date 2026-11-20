package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BriefAttachmentTests {

    private static final Instant UPLOADED = Instant.parse("2026-11-20T10:00:00Z");
    private static final byte[] PDF = "%PDF-1.7 demo".getBytes(StandardCharsets.US_ASCII);

    private final Brief brief = mock(Brief.class);

    @Test
    void theSizeIsTheNumberOfBytes() {
        BriefAttachment attachment = new BriefAttachment(brief, "brief.pdf", "application/pdf", PDF, 2, UPLOADED);

        assertThat(attachment.getSizeBytes()).isEqualTo(PDF.length);
        assertThat(attachment.getPageCount()).isEqualTo(2);
        assertThat(attachment.getContent()).isEqualTo(PDF);
    }

    @Test
    void theStoredBytesCannotBeChangedFromOutside() {
        byte[] content = PDF.clone();
        BriefAttachment attachment = new BriefAttachment(brief, "brief.pdf", "application/pdf", content, 1, UPLOADED);
        content[0] = 'X';
        attachment.getContent()[1] = 'Y';

        assertThat(attachment.getContent()).isEqualTo(PDF);
    }

    @Test
    void emptyFilesAndZeroPagesAreRejected() {
        assertThatThrownBy(() -> new BriefAttachment(brief, "brief.pdf", "application/pdf", new byte[0], 1, UPLOADED))
                .hasMessageContaining("PDF content");
        assertThatThrownBy(() -> new BriefAttachment(brief, "brief.pdf", "application/pdf", PDF, 0, UPLOADED))
                .hasMessageContaining("page count");
    }

    @Test
    void briefNameTypeAndTimeAreRequired() {
        assertThatThrownBy(() -> new BriefAttachment(null, "b.pdf", "application/pdf", PDF, 1, UPLOADED))
                .hasMessageContaining("brief");
        assertThatThrownBy(() -> new BriefAttachment(brief, " ", "application/pdf", PDF, 1, UPLOADED))
                .hasMessageContaining("file name");
        assertThatThrownBy(() -> new BriefAttachment(brief, "b.pdf", "", PDF, 1, UPLOADED))
                .hasMessageContaining("content type");
        assertThatThrownBy(() -> new BriefAttachment(brief, "b.pdf", "application/pdf", PDF, 1, null))
                .hasMessageContaining("upload time");
    }
}
