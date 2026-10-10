package com.cotizaia.ingest;

import com.cotizaia.domain.BriefChannel;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Adapter for the PDF channel. The upload service hands it the text already
 * extracted by {@link PdfBriefReader}; an optional operator note is prepended
 * so context gathered at intake time stays in the normalized brief.
 *
 * <p>The extracted text is already capped by the reader, so this adapter uses
 * the unbounded read instead of the typed-channel length cap.
 */
@Component
public class PdfBriefAdapter implements BriefSource {

    @Override
    public BriefChannel channel() {
        return BriefChannel.PDF;
    }

    @Override
    public NormalizedBrief normalize(Map<String, Object> payload) {
        String text = PayloadSupport.requireText(payload, "text");
        Object note = payload == null ? null : payload.get("note");
        if (note == null || note.toString().isBlank()) {
            return new NormalizedBrief(text);
        }
        String normalizedNote = PayloadSupport.normalizeWhitespace(note.toString());
        return new NormalizedBrief(normalizedNote + ". " + text);
    }
}
