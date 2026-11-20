package com.cotizaia.service.whatsapp;

import com.cotizaia.config.RateLimiter;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.RequirementType;
import com.cotizaia.pricing.PricingLine;
import com.cotizaia.pricing.PricingQuote;
import com.cotizaia.pricing.PricingService;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.RequirementTypeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.transaction.annotation.Transactional;

/**
 * The WhatsApp bot: someone writes "quiero una página web con pagos" and gets back a price range and the link to
 * the agency's page. It quotes from the agency's own catalog (the keywords in each requirement type's description)
 * and prices through {@link PricingService}, the same Strategy the full proposals use, so the bot never invents a
 * number the agency has not configured. The range is wide on purpose: the real proposal comes from a full brief.
 */
public class InstantQuoteService {

    /** Replies per sender and hour: enough for a conversation, too few for a reply loop with another bot. */
    static final int REPLIES_PER_HOUR = 15;
    static final BigDecimal LOW = new BigDecimal("0.85");
    static final BigDecimal HIGH = new BigDecimal("1.15");
    private static final BigDecimal ROUND_TO = new BigDecimal("50000");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_WORD = Pattern.compile("[^a-z0-9]+");

    private final WhatsappProperties properties;
    private final AgencyRepository agencies;
    private final RequirementTypeRepository types;
    private final PricingService pricing;
    private final RateLimiter limiter;

    public InstantQuoteService(WhatsappProperties properties, AgencyRepository agencies,
            RequirementTypeRepository types, PricingService pricing, RateLimiter limiter) {
        this.properties = properties;
        this.agencies = agencies;
        this.types = types;
        this.pricing = pricing;
        this.limiter = limiter;
    }

    /** The reply for this message, or empty when the bot should stay quiet (not configured, or too many). */
    @Transactional(readOnly = true, noRollbackFor = NoSuchElementException.class)
    public Optional<String> reply(String sender, String text) {
        Long agencyId = properties.agencyId();
        if (agencyId == null || !limiter.tryAcquire("whatsapp-bot:" + sender, REPLIES_PER_HOUR,
                Duration.ofHours(1)).allowed()) {
            return Optional.empty();
        }
        Optional<Agency> agency = agencies.findById(agencyId);
        if (agency.isEmpty()) {
            return Optional.empty();
        }
        List<RequirementType> catalog = types.findByServiceCatalogAgencyIdOrderByIdAsc(agencyId);
        String words = " " + normalize(text) + " ";
        List<RequirementType> wanted = catalog.stream().filter(type -> mentions(words, type)).toList();
        String greeting = "¡Hola! Soy el asistente de " + agency.get().getName() + ".";
        if (wanted.isEmpty()) {
            return Optional.of(greeting + " Cuéntame qué necesitas y te doy un precio aproximado"
                    + examples(catalog) + "." + link("Conoce nuestros servicios aquí: "));
        }
        String names = String.join(", ", wanted.stream().map(RequirementType::getName).toList());
        Optional<BigDecimal> total = total(agencyId, wanted);
        if (total.isEmpty()) {
            return Optional.of(greeting + " Te podemos ayudar con: " + names + "."
                    + link("Pide tu cotización aquí: "));
        }
        return Optional.of(greeting + "\n\nPara " + names + " el valor aproximado está entre "
                + money(round(total.get().multiply(LOW))) + " y " + money(round(total.get().multiply(HIGH)))
                + ".\nEs un estimado: el precio final depende de los detalles de tu proyecto."
                + link("Recibe tu cotización formal aquí: "));
    }

    private Optional<BigDecimal> total(Long agencyId, List<RequirementType> wanted) {
        List<PricingLine> lines = wanted.stream()
                .map(type -> new PricingLine("type-" + type.getId(), type.getName(), type.getEstimatedHours()))
                .toList();
        try {
            PricingQuote quote = pricing.quote(agencyId, null, lines);
            return Optional.of(quote.subtotal());
        } catch (NoSuchElementException noRates) {
            // An agency without rates still gets its leads; it just cannot be priced yet.
            return Optional.empty();
        }
    }

    /** True when the message holds the type's name or one of its keywords as whole words. */
    static boolean mentions(String paddedWords, RequirementType type) {
        String keywords = type.getName() + "," + (type.getDescription() == null ? "" : type.getDescription());
        return Arrays.stream(keywords.split("[,;]"))
                .map(InstantQuoteService::normalize)
                .filter(keyword -> keyword.length() >= 3)
                .anyMatch(keyword -> paddedWords.contains(" " + keyword + " "));
    }

    /** Lower case, no accents, words separated by single spaces: "¡Página WEB!" becomes "pagina web". */
    static String normalize(String text) {
        String plain = MARKS.matcher(Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD))
                .replaceAll("");
        return NOT_WORD.matcher(plain.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    private String examples(List<RequirementType> catalog) {
        List<String> names = catalog.stream().limit(4).map(RequirementType::getName).toList();
        return names.isEmpty() ? "" : ". Por ejemplo: " + String.join(", ", names);
    }

    private String link(String label) {
        return properties.siteUrl().isEmpty() ? "" : "\n" + label + properties.siteUrl();
    }

    private static BigDecimal round(BigDecimal value) {
        BigDecimal steps = value.divide(ROUND_TO, 0, RoundingMode.HALF_UP).max(BigDecimal.ONE);
        return steps.multiply(ROUND_TO);
    }

    static String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"));
        format.setMaximumFractionDigits(0);
        return format.format(value);
    }
}
