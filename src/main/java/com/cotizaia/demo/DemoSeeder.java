package com.cotizaia.demo;

import com.cotizaia.agent.knowledge.KnowledgeService;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.QuestionStatus;
import com.cotizaia.domain.Role;
import com.cotizaia.domain.ServiceCatalog;
import com.cotizaia.facade.QuotationFacade;
import com.cotizaia.facade.QuotationResult;
import com.cotizaia.mail.EmailVerificationService;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.AppUserRepository;
import com.cotizaia.service.ApprovalQueueService;
import com.cotizaia.service.AuthService;
import com.cotizaia.service.BriefUploadService;
import com.cotizaia.service.CatalogService;
import com.cotizaia.service.ClientService;
import com.cotizaia.service.RateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads the fictional demo agency of {@code seed/} (packaged as {@code demo-seed/}) when {@code demo.seed=true},
 * through the same services the API uses, so its data is encrypted at rest like any other agency's.
 *
 * <p>Runs once per database: when the demo owner's account exists it does nothing. It runs off the startup
 * thread so the health check answers while the agent processes the briefs.</p>
 */
@Component
@ConditionalOnProperty(name = "demo.seed", havingValue = "true")
public class DemoSeeder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoSeeder.class);

    static final String AGENCY = "Estudio Nariño Digital";

    private static final String OWNER = "Laura Pantoja";

    private static final String ANSWER = "El cliente confirma el alcance descrito en el brief; contenidos, fotos y "
            + "accesos los entrega el cliente y se acepta una ronda de ajustes.";

    /** Client RFP PDFs and the lifecycle state their proposal is taken to. */
    private static final Map<String, Target> PDF_BRIEFS = new LinkedHashMap<>();

    static {
        PDF_BRIEFS.put("P07", Target.ACCEPTED);
        PDF_BRIEFS.put("P12", Target.NEGOTIATING);
        PDF_BRIEFS.put("P14", Target.SENT);
        PDF_BRIEFS.put("P24", Target.REJECTED);
        PDF_BRIEFS.put("P29", Target.IN_REVIEW);
        PDF_BRIEFS.put("P30", Target.IN_REVIEW);
    }

    /** Typed briefs; each arrives through its project's channel in the manifest. */
    private static final List<String> TYPED_BRIEFS = List.of("P02", "P03", "P05", "P09");

    private static final Map<BriefChannel, String> PAYLOAD_FIELD = Map.of(
            BriefChannel.EMAIL, "body", BriefChannel.WHATSAPP, "message", BriefChannel.WEB_FORM, "description");

    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");

    private final AuthService auth;

    private final AppUserRepository users;

    private final AgencyRepository agencies;

    private final RateService rates;

    private final CatalogService catalogs;

    private final ClientService clients;

    private final KnowledgeService knowledge;

    private final BriefUploadService uploads;

    private final QuotationFacade facade;

    private final ApprovalQueueService approval;

    private final ObjectMapper mapper = new ObjectMapper();

    private final EmailVerificationService verification;

    private final String email;

    private final String password;

    public DemoSeeder(AuthService auth, AppUserRepository users, AgencyRepository agencies, RateService rates, CatalogService catalogs, ClientService clients,
            KnowledgeService knowledge, BriefUploadService uploads, QuotationFacade facade,
            ApprovalQueueService approval, EmailVerificationService verification,
            @Value("${demo.email}") String email,
            @Value("${demo.password}") String password) {
        this.auth = auth;
        this.users = users;
        this.agencies = agencies;
        this.rates = rates;
        this.catalogs = catalogs;
        this.clients = clients;
        this.knowledge = knowledge;
        this.uploads = uploads;
        this.facade = facade;
        this.approval = approval;
        this.verification = verification;
        this.email = email;
        this.password = password;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedInBackground() {
        Thread thread = new Thread(() -> {
            try {
                seed();
            } catch (RuntimeException e) {
                LOGGER.error("Demo agency could not be loaded", e);
            }
        }, "demo-seed");
        thread.setDaemon(true);
        thread.start();
    }

    /** Loads the demo agency into an empty database only; returns whether it was created. */
    public synchronized boolean seed() {
        // Agency names are encrypted at rest, so "is the demo already there" cannot be asked by name, and an email
        // check alone misses a demo registered under another address (production uses demo@estudio-narino.co). A
        // database that already holds any agency is left alone; scripts/seed_demo.py loads the demo into it instead.
        if (users.existsByLoginEmail(email.trim().toLowerCase(Locale.ROOT)) || agencies.count() > 0) {
            LOGGER.info("Demo agency not loaded: the database already has agencies (demo email {})", email);
            return false;
        }
        LOGGER.info("Loading demo agency '{}'", AGENCY);
        JsonNode manifest = json("manifest.json");
        Long agencyId = auth.register(AGENCY, OWNER, email, password).agencyId();
        // Spec 16: the demo owner is trusted, so MAIL_VERIFICATION_REQUIRED=true never locks the demo out.
        verification.markVerified(email);

        rate(agencyId, "Desarrollo web", manifest.path("rateCopPerHour").asLong());
        rate(agencyId, "Diseño y marca", 85_000);
        rate(agencyId, "Gestión de proyecto", 110_000);

        ServiceCatalog catalog = catalogs.create(agencyId, "Servicios digitales",
                "Sitios web, comercio electrónico, integraciones y marca para pymes de Nariño.", null);
        for (JsonNode entry : manifest.path("catalog")) {
            catalogs.addType(agencyId, catalog.getId(), entry.path("name").asText(), entry.path("keywords").asText(),
                    BigDecimal.valueOf(entry.path("catalogHours").asLong()));
        }

        Map<String, JsonNode> projects = new HashMap<>();
        Map<String, JsonNode> contents = new HashMap<>();
        Map<String, Long> clientIds = new HashMap<>();
        for (JsonNode project : manifest.path("projects")) {
            String pid = project.path("projectId").asText();
            JsonNode content = json("content/" + pid + ".json");
            String contact = content.path("contactName").asText();
            String company = content.path("clientName").asText();
            projects.put(pid, project);
            contents.put(pid, content);
            clientIds.put(pid, clients.create(agencyId, contact,
                    slug(contact, ".") + "@" + slug(company, "-") + ".test", company).getId());
        }

        int indexed = 0;
        for (JsonNode project : manifest.path("projects")) {
            if ("train".equals(project.path("split").asText())) {
                String name = project.path("projectId").asText() + "-propuesta.pdf";
                knowledge.upload(agencyId, name, bytes("pdf/train/" + name));
                indexed++;
            }
        }

        for (Map.Entry<String, Target> brief : PDF_BRIEFS.entrySet()) {
            String name = brief.getKey() + "-brief.pdf";
            Long briefId = uploads.upload(agencyId, clientIds.get(brief.getKey()), name,
                    bytes("pdf/holdout/" + name), "Solicitud recibida en PDF").brief().getId();
            QuotationResult result = answerBlockingQuestions(agencyId, facade.processBrief(agencyId, briefId));
            advance(agencyId, result.proposalId(), brief.getValue());
        }
        for (String pid : TYPED_BRIEFS) {
            BriefChannel channel = BriefChannel.valueOf(projects.get(pid).path("channel").asText());
            Map<String, Object> payload = Map.of(PAYLOAD_FIELD.get(channel), contents.get(pid).path("brief").asText());
            answerBlockingQuestions(agencyId, facade.ingestAndProcess(agencyId, channel, clientIds.get(pid), payload));
        }

        LOGGER.info("Demo agency '{}' loaded: {} clients, {} past proposals in memory, {} briefs. Sign in as {}",
                AGENCY, clientIds.size(), indexed, PDF_BRIEFS.size() + TYPED_BRIEFS.size(), email);
        return true;
    }

    /** The agent halts on blocking ambiguities; the agency answers them as a reviewer would. */
    private QuotationResult answerBlockingQuestions(Long agencyId, QuotationResult result) {
        for (int round = 0; round < 3 && result.halted(); round++) {
            QuotationResult current = result;
            var open = current.questions().stream()
                    .filter(q -> q.blocking() && q.status() == QuestionStatus.OPEN).findFirst();
            if (open.isEmpty()) {
                break;
            }
            result = facade.answerQuestion(agencyId, current.briefId(), open.get().id(), ANSWER);
        }
        return result;
    }

    private void advance(Long agencyId, Long proposalId, Target target) {
        if (proposalId == null) {
            return;
        }
        try {
            switch (target) {
                case IN_REVIEW -> { }
                case SENT -> {
                    approval.approve(agencyId, proposalId);
                    approval.send(agencyId, proposalId);
                }
                case NEGOTIATING -> {
                    approval.approve(agencyId, proposalId);
                    approval.send(agencyId, proposalId);
                    approval.negotiate(agencyId, proposalId);
                }
                case ACCEPTED -> {
                    approval.approve(agencyId, proposalId);
                    approval.send(agencyId, proposalId);
                    approval.accept(agencyId, proposalId);
                }
                case REJECTED -> approval.reject(agencyId, proposalId);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Demo proposal {} stopped before {}: {}", proposalId, target, e.getMessage());
        }
    }

    private void rate(Long agencyId, String roleName, long copPerHour) {
        Role role = rates.createRole(agencyId, roleName);
        rates.createRate(agencyId, role.getId(), copPerHour, null, null);
    }

    private JsonNode json(String path) {
        try (InputStream in = resource(path)) {
            return mapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Demo seed file missing: " + path, e);
        }
    }

    private byte[] bytes(String path) {
        try (InputStream in = resource(path)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Demo seed file missing: " + path, e);
        }
    }

    private static InputStream resource(String path) throws IOException {
        return new ClassPathResource("demo-seed/" + path).getInputStream();
    }

    private static String slug(String text, String separator) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        Matcher words = WORD.matcher(plain);
        StringBuilder out = new StringBuilder();
        while (words.find()) {
            if (!out.isEmpty()) {
                out.append(separator);
            }
            out.append(words.group());
        }
        return out.toString();
    }

    private enum Target {
        IN_REVIEW, SENT, NEGOTIATING, ACCEPTED, REJECTED
    }
}
