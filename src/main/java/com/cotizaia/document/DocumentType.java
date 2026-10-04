package com.cotizaia.document;

/** Identifies each persisted PDF and its stable download name prefix. */
public enum DocumentType {
    PROPOSAL("propuesta"),
    CONTRACT("contrato"),
    SCHEDULE("cronograma");

    private final String fileNamePrefix;

    DocumentType(String fileNamePrefix) {
        this.fileNamePrefix = fileNamePrefix;
    }

    public String fileNamePrefix() {
        return fileNamePrefix;
    }
}
