package com.cotizaia.agent.whatsapp;

import java.util.Locale;

/**
 * What the bot answers: the text, and optionally a sticker of the CotizaIA robot to send before it. The backend only
 * names the sticker; the WhatsApp service holds the images (whatsapp-service/stickers).
 */
public record BotReply(String text, Sticker sticker) {

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
