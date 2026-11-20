package com.cotizaia.service.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.config.RateLimiter;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Role;
import com.cotizaia.domain.ServiceCatalog;
import com.cotizaia.pricing.PricingLine;
import com.cotizaia.pricing.PricingService;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.RequirementTypeRepository;
import com.cotizaia.service.CatalogService;
import com.cotizaia.service.RateService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The bot quotes from the agency's own catalog and rates, through the same pricing Strategy as full proposals.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InstantQuoteServiceTests {

    private static final String SITE = "https://cotizaia.example.com";

    @Autowired
    private AgencyRepository agencies;

    @Autowired
    private RequirementTypeRepository types;

    @Autowired
    private PricingService pricing;

    @Autowired
    private RateService rates;

    @Autowired
    private CatalogService catalogs;

    @Test
    void aWebsiteRequestGetsAPriceRangeFromTheCatalogAndTheLink() {
        Long agencyId = agencyWithCatalog(true);
        InstantQuoteService bot = bot(agencyId);

        String reply = bot.reply("573001234567@s.whatsapp.net", "Hola, quiero hacer una PÁGINA WEB para mi negocio")
                .orElseThrow();

        BigDecimal total = pricing.quote(agencyId, null,
                List.of(new PricingLine("web", "Sitio web informativo", new BigDecimal("40")))).subtotal();
        assertThat(reply).contains("Estudio Prueba", "Sitio web informativo", "aproximado",
                InstantQuoteService.money(rounded(total.multiply(InstantQuoteService.LOW))),
                InstantQuoteService.money(rounded(total.multiply(InstantQuoteService.HIGH))), SITE);
        assertThat(reply).doesNotContain("Tienda en linea");
    }

    @Test
    void severalServicesInOneMessageAreQuotedTogether() {
        InstantQuoteService bot = bot(agencyWithCatalog(true));

        String reply = bot.reply("sender", "necesito una tienda con pagos por PSE").orElseThrow();

        assertThat(reply).contains("Tienda en linea", "Pasarela de pagos").doesNotContain("Sitio web informativo");
    }

    @Test
    void anUnclearMessageGetsTheMenuAndTheLink() {
        InstantQuoteService bot = bot(agencyWithCatalog(true));

        String reply = bot.reply("sender", "buenas tardes").orElseThrow();

        assertThat(reply).contains("Cuéntame qué necesitas", "Sitio web informativo", SITE).doesNotContain("$");
    }

    @Test
    void anAgencyWithoutRatesStillAnswersWithoutAPrice() {
        InstantQuoteService bot = bot(agencyWithCatalog(false));

        String reply = bot.reply("sender", "quiero una pagina web").orElseThrow();

        assertThat(reply).contains("Sitio web informativo", SITE).doesNotContain("aproximado");
    }

    @Test
    void theBotStaysQuietWhenNotConfiguredOrFlooded() {
        assertThat(bot(null).reply("sender", "quiero una pagina web")).isEmpty();

        InstantQuoteService bot = bot(agencyWithCatalog(true));
        for (int reply = 0; reply < InstantQuoteService.REPLIES_PER_HOUR; reply++) {
            assertThat(bot.reply("loop", "hola")).isPresent();
        }
        assertThat(bot.reply("loop", "hola")).isEmpty();
        assertThat(bot.reply("someone-else", "hola")).isPresent();
    }

    @Test
    void keywordsMatchWholeWordsWithoutAccents() {
        assertThat(InstantQuoteService.normalize("¡Página WEB, ya!")).isEqualTo("pagina web ya");
        Long agencyId = agencyWithCatalog(true);
        var web = types.findByServiceCatalogAgencyIdOrderByIdAsc(agencyId).get(0);

        assertThat(InstantQuoteService.mentions(" una pagina web ", web)).isTrue();
        assertThat(InstantQuoteService.mentions(" paginaweb ", web)).isFalse();
        assertThat(InstantQuoteService.mentions(" visitamos el sitio ", web)).isTrue();
    }

    private InstantQuoteService bot(Long agencyId) {
        WhatsappProperties properties = new WhatsappProperties("", null, agencyId, SITE);
        return new InstantQuoteService(properties, agencies, types, pricing, new RateLimiter(Clock.systemUTC()));
    }

    private Long agencyWithCatalog(boolean withRate) {
        Long agencyId = agencies.saveAndFlush(new Agency("Estudio Prueba")).getId();
        if (withRate) {
            Role role = rates.createRole(agencyId, "Desarrollo web");
            rates.createRate(agencyId, role.getId(), 95_000, Instant.now().minus(Duration.ofDays(1)), null);
        }
        ServiceCatalog catalog = catalogs.create(agencyId, "Servicios digitales", "Sitios y tiendas", null);
        catalogs.addType(agencyId, catalog.getId(), "Sitio web informativo", "pagina web, sitio, landing",
                new BigDecimal("40"));
        catalogs.addType(agencyId, catalog.getId(), "Tienda en linea", "tienda, ecommerce, carrito",
                new BigDecimal("80"));
        catalogs.addType(agencyId, catalog.getId(), "Pasarela de pagos", "pagos, pasarela, PSE",
                new BigDecimal("24"));
        return agencyId;
    }

    private static BigDecimal rounded(BigDecimal value) {
        BigDecimal step = new BigDecimal("50000");
        return value.divide(step, 0, RoundingMode.HALF_UP).max(BigDecimal.ONE).multiply(step);
    }
}
