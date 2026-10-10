package com.cotizaia.ingest;

/**
 * Thrown before parsing when an upload exceeds the size limit, so a hostile
 * multi-gigabyte file never reaches the PDF parser. Mapped to HTTP 413.
 */
public class UploadTooLargeException extends RuntimeException {

    public UploadTooLargeException(String message) {
        super(message);
    }
}
