package com.cotizaia.document;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Factory Method creator (project.txt section 6): registers one ConcreteClass per document type,
 * keeping generator selection in one place and rejecting ambiguous registrations.
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
