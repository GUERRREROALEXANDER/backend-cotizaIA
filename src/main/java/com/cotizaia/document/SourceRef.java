package com.cotizaia.document;

/** A numbered citation to one retrieved passage: the source document and, when known, its page. */
public record SourceRef(int number, String documentName, Integer page) {
}
