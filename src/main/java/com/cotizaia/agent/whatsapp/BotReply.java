package com.cotizaia.agent.whatsapp;

import com.cotizaia.document.DocumentType;
import java.util.List;
import java.util.Locale;

/**
 * What the bot answers: the text, optionally a sticker of the CotizaIA robot to send before it, and the PDFs to send
 * after it. The backend only names the sticker (the WhatsApp service holds the images, whatsapp-service/stickers) and
 * the documents (the service downloads each one from POST /api/whatsapp/bot/document).
 */
public record BotReply(String text, Sticker sticker, List<Document> documents) {

    public BotReply {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    public BotReply(String text, Sticker sticker) {
        this(text, sticker, List.of());
    }

    /** A stored proposal PDF; the file name matches the one the app downloads (DocumentService). */
    public record Document(Long proposalId, DocumentType type, String fileName) {

        /** The proposal and its schedule. The contract waits until the client accepts: before that it is a draft. */
        static List<Document> forQuote(Long proposalId) {
            return List.of(of(proposalId, DocumentType.PROPOSAL), of(proposalId, DocumentType.SCHEDULE));
        }

        private static Document of(Long proposalId, DocumentType type) {
            return new Document(proposalId, type, type.fileNamePrefix() + "-" + proposalId + ".pdf");
        }
    }

    /** The robot's poses; the name is the image file in whatsapp-service/stickers. */
    public enum Sticker {
        SALUDO, PENSANDO, LISTO, COTIZAR;

        public String fileName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static BotReply text(String text) {
        return new BotReply(text, null);
    }

    public static BotReply withSticker(String text, Sticker sticker) {
        return new BotReply(text, sticker);
    }
}
