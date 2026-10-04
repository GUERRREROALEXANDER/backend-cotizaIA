package com.cotizaia.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Checks complete factory registration and prevents type branches from spreading across source files. */
class DocumentFactoryTests {

    @Test
    void resolvesEveryTypeAndRejectsDuplicates() {
        DocumentFactory factory = new DocumentFactory(List.of(new ProposalDocumentGenerator(),
                new ContractDocumentGenerator(), new ScheduleDocumentGenerator()));
        for (DocumentType type : DocumentType.values()) {
            assertThat(factory.create(type).type()).isEqualTo(type);
        }
        assertThatThrownBy(() -> new DocumentFactory(List.of(new ProposalDocumentGenerator(),
                new ProposalDocumentGenerator()))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keepsTypeSelectionInsideFactory() throws Exception {
        Pattern branch = Pattern.compile("==\\s*DocumentType\\.|DocumentType\\.\\w+\\.equals|"
                + "case\\s+(?:PROPOSAL|CONTRACT|SCHEDULE)\\s*[:\\-]");
        try (Stream<Path> paths = Files.walk(Path.of("src", "main", "java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("DocumentFactory.java")).toList()) {
                assertThat(branch.matcher(Files.readString(path)).find()).as(path.toString()).isFalse();
            }
        }
    }
}
