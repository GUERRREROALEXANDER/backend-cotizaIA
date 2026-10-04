package com.cotizaia.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentIsolationTests {

    @Test
    void corePackagesNeverImportAgentCode() throws Exception {
        Path root = Path.of("src/main/java/com/cotizaia");
        for (String name : List.of("domain", "pricing", "service", "document", "notification", "payment")) {
            Path directory = root.resolve(name);
            if (Files.isDirectory(directory)) {
                try (var files = Files.walk(directory)) {
                    for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                        assertThat(Files.readString(file)).as(file.toString())
                                .doesNotContain("import com.cotizaia.agent.");
                    }
                }
            }
        }
    }
}
