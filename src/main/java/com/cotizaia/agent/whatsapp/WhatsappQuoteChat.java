package com.cotizaia.agent.whatsapp;

import com.cotizaia.agent.PipelineRunRecorder.PipelineFailedException;
import com.cotizaia.crypto.BlindIndex;
import com.cotizaia.domain.AppUser;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.QuestionStatus;
import com.cotizaia.domain.WhatsappQuoteSession;
import com.cotizaia.domain.WhatsappQuoteSession.Step;
import com.cotizaia.facade.QuotationFacade;
import com.cotizaia.facade.QuotationResult;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.repository.WhatsappQuoteSessionRepository;
import com.cotizaia.service.ClientService;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quoting by WhatsApp for agency users whose number is verified. The chat asks for the client's name, their email
 * (optional) and the project, then runs the same {@link QuotationFacade} as the web app, so the quotation goes
 * through the whole agent pipeline and pricing Strategy. When the agent needs a clarification, the chat relays its
 * question and sends the answer back. The result is a proposal waiting for review in the app, never sent on its own.
 *
 * <p>State Machine: each message moves the stored {@link WhatsappQuoteSession} one step; "cancelar" goes back to
 * the start from any step, and a chat left alone for two hours starts over.
 */
public class WhatsappQuoteChat {

    static final int MIN_DESCRIPTION = 15;
    static final int MAX_NAME = 120;
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}");
    private static final Set<String> START = Set.of("cotizar", "cotizacion", "nueva cotizacion", "nueva", "cotiza");
    private static final Set<String> CANCEL = Set.of("cancelar", "cancela", "salir", "reiniciar");
    private static final Set<String> NO_EMAIL = Set.of("no", "ninguno", "no tiene", "sin correo", "no tengo", "n/a");
    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsappQuoteChat.class);

    private final QuotationFacade quotations;
    private final ClientService clientService;
    private final ClientRepository clients;
    private final AgencyRepository agencies;
    private final WhatsappQuoteSessionRepository sessions;
    private final BlindIndex blindIndex;
    private final String siteUrl;
    private final Clock clock;
    /** Users whose quotation is running right now: a second message waits instead of starting another. */
    private final Set<Long> busy = ConcurrentHashMap.newKeySet();

    public WhatsappQuoteChat(QuotationFacade quotations, ClientService clientService, ClientRepository clients,
            AgencyRepository agencies, WhatsappQuoteSessionRepository sessions, BlindIndex blindIndex,
            String siteUrl, Clock clock) {
        this.quotations = quotations;
        this.clientService = clientService;
        this.clients = clients;
        this.agencies = agencies;
        this.sessions = sessions;
        this.blindIndex = blindIndex;
        this.siteUrl = siteUrl == null ? "" : siteUrl;
        this.clock = clock;
    }

    /** The reply to a message from this agency user. */
    public String reply(AppUser user, String text) {
        Long userId = user.getId();
        if (!busy.add(userId)) {
            return "Sigo trabajando en su cotización. En cuanto termine le escribo el resultado.";
        }
        try {
            WhatsappQuoteSession session = sessions.findById(userId)
                    .orElseGet(() -> new WhatsappQuoteSession(userId, clock.instant()));
            String reply = advance(user, session, text.trim());
            sessions.save(session);
            return reply;
        } finally {
            busy.remove(userId);
        }
    }

    private String advance(AppUser user, WhatsappQuoteSession session, String text) {
        String plain = plain(text);
        Step step = session.stepAt(clock.instant());
        if (CANCEL.contains(plain)) {
            session.reset(clock.instant());
            return "Listo, cancelé la cotización. Escriba *cotizar* cuando quiera empezar otra.";
        }
        if (step == Step.IDLE && !START.contains(plain)) {
            session.reset(clock.instant());
            return menu(user);
        }
        return switch (step) {
            case IDLE -> {
                session.askClientName(clock.instant());
                yield "Vamos a crear una cotización 📝\n¿Cómo se llama el cliente? (persona o empresa)\n"
                        + "_Escriba *cancelar* en cualquier momento para salir._";
            }
            case CLIENT_NAME -> clientName(session, text);
            case CLIENT_EMAIL -> clientEmail(user, session, text, plain);
            case DESCRIPTION -> description(user, session, text);
            case QUESTION -> answer(user, session, text);
        };
    }

    private String clientName(WhatsappQuoteSession session, String text) {
        if (text.length() < 2 || text.length() > MAX_NAME) {
            return "Escriba el nombre del cliente (entre 2 y " + MAX_NAME + " caracteres).";
        }
        session.askClientEmail(text, clock.instant());
        return "¿Cuál es el correo de " + text + "? Si no lo tiene, escriba *no*.";
    }

    private String clientEmail(AppUser user, WhatsappQuoteSession session, String text, String plain) {
        Long agencyId = user.getAgency().getId();
        String email = null;
        if (!NO_EMAIL.contains(plain)) {
            if (!EMAIL.matcher(text).matches() || text.length() > 255) {
                return "Ese correo no parece válido. Escríbalo de nuevo, o *no* si el cliente no tiene.";
            }
            email = text.toLowerCase(Locale.ROOT);
        }
        Client client = email == null ? null
                : clients.findByAgencyIdAndEmailHash(agencyId, blindIndex.hash(email)).orElse(null);
        boolean known = client != null;
        if (client == null) {
            client = clientService.create(agencyId, session.getClientName(), email, null);
        }
        session.askDescription(client.getId(), client.getName(), clock.instant());
        return (known ? "Encontré a " + client.getName() + " en sus clientes. " : "Registré a "
                + session.getClientName() + " como cliente. ")
                + "Ahora cuénteme el proyecto: qué necesita, para qué negocio, qué funciones y si tiene fecha límite.";
    }

    private String description(AppUser user, WhatsappQuoteSession session, String text) {
        if (text.length() < MIN_DESCRIPTION) {
            return "Cuénteme un poco más del proyecto para poder cotizarlo (por ejemplo: \"una página web para mi "
                    + "restaurante con menú y reservas\").";
        }
        return run(session, () -> quotations.ingestAndProcess(user.getAgency().getId(), BriefChannel.WHATSAPP,
                session.getClientId(), Map.of("message", text)));
    }

    private String answer(AppUser user, WhatsappQuoteSession session, String text) {
        return run(session, () -> quotations.answerQuestion(user.getAgency().getId(), session.getBriefId(),
                session.getQuestionId(), text));
    }

    private String run(WhatsappQuoteSession session, java.util.function.Supplier<QuotationResult> quotation) {
        QuotationResult result;
        try {
            result = quotation.get();
        } catch (PipelineFailedException failed) {
            session.reset(clock.instant());
            return "No pude terminar la cotización: la IA no respondió. El brief quedó guardado; puede reintentarlo "
                    + "desde la Bandeja en CotizaIA" + link("/#/bandeja") + ".";
        } catch (RuntimeException failure) {
            LOGGER.warn("WhatsApp quotation failed: {}", failure.getClass().getSimpleName());
            session.reset(clock.instant());
            return "Algo falló al crear la cotización. Intente de nuevo con *cotizar* o hágala desde CotizaIA"
                    + link("") + ".";
        }
        Optional<QuotationResult.QuestionView> question = result.questions().stream()
                .filter(open -> open.blocking() && open.status() == QuestionStatus.OPEN).findFirst();
        if (result.halted() && question.isPresent()) {
            session.askQuestion(result.briefId(), question.get().id(), clock.instant());
            return "Para cotizar bien necesito un dato más:\n" + question.get().question();
        }
        // Read before reset(): it clears the client name.
        String client = clientLabel(session, result);
        session.reset(clock.instant());
        if (result.halted()) {
            return "La cotización #" + result.proposalId() + " para " + client + " quedó pendiente: termínela en "
                    + "CotizaIA" + link("/#/propuestas") + ".";
        }
        return "✅ Cotización #" + result.proposalId() + " para " + client + "\n"
                + "Total: " + money(result.total()) + "\n"
                + "Estado: " + status(result.proposalStatus()) + "\n"
                + "Revísela y apruébela antes de enviarla al cliente" + link("/#/propuestas") + "\n\n"
                + "Escriba *cotizar* para hacer otra.";
    }

    private String clientLabel(WhatsappQuoteSession session, QuotationResult result) {
        return session.getClientName() != null ? session.getClientName() : "el brief #" + result.briefId();
    }

    private String menu(AppUser user) {
        String agency = agencies.findById(user.getAgency().getId()).map(found -> " de " + found.getName()).orElse("");
        String first = user.getFullName() == null ? "" : " " + user.getFullName().trim().split("\\s+")[0];
        return "Hola" + first + " 👋 Este número está vinculado a su cuenta" + agency + ".\n"
                + "Escriba *cotizar* para crear una cotización desde aquí.";
    }

    private String link(String path) {
        return siteUrl.isEmpty() ? "" : ": " + siteUrl + path;
    }

    static String status(ProposalStatus status) {
        if (status == null) {
            return "en proceso";
        }
        return switch (status) {
            case RECEIVED -> "recibida";
            case ANALYZING -> "analizando";
            case QUOTED -> "cotizada";
            case IN_REVIEW -> "lista para su revisión";
            case SENT -> "enviada";
            case NEGOTIATING -> "en negociación";
            case ACCEPTED -> "aceptada";
            case CONTRACT_ISSUED -> "contrato emitido";
            case REJECTED -> "rechazada";
            case EXPIRED -> "vencida";
        };
    }

    static String money(BigDecimal value) {
        if (value == null) {
            return "por calcular";
        }
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"));
        format.setMaximumFractionDigits(0);
        return format.format(value);
    }

    /** Lower case, no accents or punctuation: "¡Cotizar!" is "cotizar". */
    static String plain(String text) {
        String noMarks = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return noMarks.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/ ]+", " ").replaceAll("\\s+", " ").trim();
    }
}
