package com.cotizaia.document;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Simple Factory (a registry): maps each {@link DocumentType} to its generator, keeping selection in one place
 * and rejecting ambiguous registrations. It used to be labelled "Factory Method", which it is not: nothing is
 * deferred to a subclass. The GoF Factory Method lives in {@code agent.knowledge.KnowledgeIngestor#createChunker()}.
 */
@Component
public class DocumentFactory {

    private final EnumMap<DocumentType, AbstractDocumentTemplate> generators = new EnumMap<>(DocumentType.class);

    public DocumentFactory(List<AbstractDocumentTemplate> generators) {
        for (AbstractDocumentTemplate generator : generators) {
            if (this.generators.putIfAbsent(generator.type(), generator) != null) {
                throw new IllegalStateException("Duplicate document generator: " + generator.type());
            }
        }
    }

    public AbstractDocumentTemplate create(DocumentType type) {
        AbstractDocumentTemplate generator = generators.get(type);
        if (generator == null) {
            throw new IllegalArgumentException("No document generator for " + type);
        }
        return generator;
    }

    public Map<DocumentType, byte[]> renderAll(DocumentData data) {
        EnumMap<DocumentType, byte[]> rendered = new EnumMap<>(DocumentType.class);
        for (DocumentType type : DocumentType.values()) {
            rendered.put(type, create(type).render(data));
        }
        return rendered;
    }
}
