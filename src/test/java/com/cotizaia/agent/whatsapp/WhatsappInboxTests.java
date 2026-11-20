package com.cotizaia.agent.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WhatsappInboxTests {

    @Test
    void theSalesRobotWavesAtGreetingsAndShowsAQuoteWithTheLink() {
        assertThat(WhatsappInbox.salesSticker("¡Hola!", "Hola, soy el asistente")).isEqualTo(BotReply.Sticker.SALUDO);
        assertThat(WhatsappInbox.salesSticker("Buenos días", "Bienvenido")).isEqualTo(BotReply.Sticker.SALUDO);
        assertThat(WhatsappInbox.salesSticker("quiero una web", "Envíela aquí: https://cotizaia.app"))
                .isEqualTo(BotReply.Sticker.COTIZAR);
        assertThat(WhatsappInbox.salesSticker("cuanto vale", "Depende del alcance")).isNull();
        assertThat(WhatsappInbox.salesSticker("holanda es bonito", "Sí")).isNull();
    }

    @Test
    void stickerNamesAreTheImageFiles() {
        assertThat(BotReply.Sticker.PENSANDO.fileName()).isEqualTo("pensando");
    }
}
