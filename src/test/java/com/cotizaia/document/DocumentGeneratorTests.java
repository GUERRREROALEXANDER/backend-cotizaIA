package com.cotizaia.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.domain.DefaultContractClauses;
import com.cotizaia.pricing.PricingModel;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies shared framing and distinct Template Method sections without a Spring context. */
class DocumentGeneratorTests {

    @Test
    void rendersThreeDistinctPdfDocuments() throws Exception {
        DocumentData data = new DocumentData("Agencia Demo", "Restaurante El Sabor", "Brief #12",
                LocalDate.of(2026, 10, 4), "Sistema de reservas", PricingModel.HOURLY,
                List.of(new DocumentData.LineData("Reservas", new BigDecimal("10"),
                                new BigDecimal("100"), new BigDecimal("1000")),
                        new DocumentData.LineData("Menu", new BigDecimal("5"),
                                new BigDecimal("100"), new BigDecimal("500"))),
                new BigDecimal("1500"), List.of(new DocumentData.ExtraData("Garantia", new BigDecimal("100"))),
                new BigDecimal("1600"), new BigDecimal("800"),
                List.of(new DocumentData.PhaseData("Diseno", 1, 1, 1),
                        new DocumentData.PhaseData("Desarrollo", 2, 2, 3),
                        new DocumentData.PhaseData("Entrega", 1, 4, 4)),
                DefaultContractClauses.DEFAULT_CLAUSES);
        DocumentFactory factory = new DocumentFactory(List.of(new ProposalDocumentGenerator(),
                new ContractDocumentGenerator(), new ScheduleDocumentGenerator()));
        Map<DocumentType, byte[]> rendered = factory.renderAll(data);
        Path output = Path.of("target", "generated-docs");
        Files.createDirectories(output);
        Map<DocumentType, String> titles = Map.of(DocumentType.PROPOSAL, "Propuesta comercial",
                DocumentType.CONTRACT, "Contrato de prestacion de servicios",
                DocumentType.SCHEDULE, "Cronograma del proyecto");
        for (DocumentType type : DocumentType.values()) {
            byte[] bytes = rendered.get(type);
            assertThat(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
            Files.write(output.resolve(type.fileNamePrefix() + ".pdf"), bytes);
            PdfReader reader = new PdfReader(bytes);
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            reader.close();
            assertThat(text).contains("Agencia Demo", titles.get(type),
                    "Documento generado por CotizaIA - borrador sujeto a aprobacion humana.",
                    "Pagos simulados: no se mueve dinero real.");
            for (DocumentType other : DocumentType.values()) {
                if (other != type) {
                    assertThat(text).doesNotContain(titles.get(other));
                }
            }
        }
    }
}
