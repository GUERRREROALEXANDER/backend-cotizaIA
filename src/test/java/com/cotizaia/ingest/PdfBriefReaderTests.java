package com.cotizaia.ingest;

import static com.cotizaia.ingest.PdfFixtures.numberedPages;
import static com.cotizaia.ingest.PdfFixtures.pdf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Contract of the PDF reader behind {@code POST /api/briefs/upload}: it turns
 * an uploaded RFP into brief text, reports pages it could not read, and
 * enforces the size, page and text limits before anything reaches the agent.
 */
class PdfBriefReaderTests {

    private final PdfBriefReader reader = new PdfBriefReader();

    @Test
    void extractsTextAndPageCount() {
        PdfExtraction result = reader.read(pdf("Necesito una tienda online", "Con pagos y envios"));

        assertThat(result.pages()).isEqualTo(2);
        assertThat(result.text()).contains("Necesito una tienda online").contains("Con pagos y envios");
        assertThat(result.truncated()).isFalse();
        assertThat(result.unreadablePages()).isEmpty();
    }

    @Test
    void reportsPagesWithoutTextAsUnreadableUsingOneBasedNumbers() {
        PdfExtraction result = reader.read(pdf("Requisitos del proyecto", "", "Presupuesto aproximado", ""));

        assertThat(result.pages()).isEqualTo(4);
        assertThat(result.unreadablePages()).containsExactly(2, 4);
        assertThat(result.text()).contains("Requisitos del proyecto").contains("Presupuesto aproximado");
    }

    @Test
    void truncatesTextOverTheLimitAndFlagsIt() {
        String longPage = "palabra ".repeat(3_200);
        PdfExtraction result = reader.read(pdf(longPage, longPage));

        assertThat(result.truncated()).isTrue();
        assertThat(result.text()).hasSizeLessThanOrEqualTo(PdfBriefReader.MAX_TEXT_CHARS);
        assertThat(result.text()).hasSizeGreaterThan(PdfBriefReader.MAX_TEXT_CHARS - 100);
    }

    @Test
    void acceptsExactlyTheMaximumPageCount() {
        PdfExtraction result = reader.read(pdf(numberedPages(PdfBriefReader.MAX_PAGES)));

        assertThat(result.pages()).isEqualTo(PdfBriefReader.MAX_PAGES);
    }

    @Test
    void rejectsMoreThanTheMaximumPageCount() {
        byte[] tooManyPages = pdf(numberedPages(PdfBriefReader.MAX_PAGES + 1));

        assertThatThrownBy(() -> reader.read(tooManyPages))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(PdfBriefReader.MAX_PAGES));
    }

    @Test
    void rejectsBytesThatAreNotAPdf() {
        byte[] notPdf = "Hola, esto es un .txt renombrado".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> reader.read(notPdf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    void rejectsEmptyUpload() {
        assertThatThrownBy(() -> reader.read(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAPdfWithNoExtractableTextAtAll() {
        assertThatThrownBy(() -> reader.read(pdf("", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }

    @Test
    void rejectsFilesOverTheSizeLimitBeforeParsing() {
        byte[] huge = new byte[PdfBriefReader.MAX_BYTES + 1];
        byte[] header = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, huge, 0, header.length);
        Arrays.fill(huge, header.length, huge.length, (byte) ' ');

        assertThatThrownBy(() -> reader.read(huge))
                .isInstanceOf(UploadTooLargeException.class);
    }
}
