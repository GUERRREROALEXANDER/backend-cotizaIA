package com.cotizaia.notification;

import com.cotizaia.domain.AppUser;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.UserRole;
import com.cotizaia.domain.WhatsappContact;
import com.cotizaia.repository.AppUserRepository;
import com.cotizaia.repository.WhatsappContactRepository;
import com.cotizaia.service.whatsapp.WhatsappOutbox;
import com.cotizaia.service.whatsapp.WhatsappProperties;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * ConcreteObserver for WhatsApp (project.txt section 6: Observer). It joins dispatch just by being a bean: the
 * Subject and the other observers did not change. It only writes to the outbox, inside the transition's
 * transaction, so a notice exists exactly when the state change commits; the WhatsApp service sends it later.
 * Only the agency owner's verified and enabled number is messaged, and only for the moves they act on.
 */
@Component
public class WhatsappObserver implements ProposalObserver {

    private final AppUserRepository users;
    private final WhatsappContactRepository contacts;
    private final WhatsappOutbox outbox;
    private final WhatsappProperties properties;

    public WhatsappObserver(AppUserRepository users, WhatsappContactRepository contacts, WhatsappOutbox outbox,
            WhatsappProperties properties) {
        this.users = users;
        this.contacts = contacts;
        this.outbox = outbox;
        this.properties = properties;
    }

    @Override
    public String channel() {
        return "WHATSAPP";
    }

    @Override
    public void onStateChanged(ProposalStateChangedEvent event) {
        Optional<String> text = text(event);
        if (text.isEmpty()) {
            return;
        }
        users.findByAgencyIdAndRole(event.agencyId(), UserRole.OWNER)
                .map(AppUser::getId)
                .flatMap(contacts::findById)
                .filter(WhatsappContact::isEnabled)
                .ifPresent(contact -> outbox.queue(contact.getPhone(), text.get(), properties.noticeLifetime()));
    }

    /** The message for a move the owner acts on; empty for the rest. */
    static Optional<String> text(ProposalStateChangedEvent event) {
        String quote = "la cotización #" + event.proposalId() + " de " + event.clientName();
        String total = formatTotal(event.total());
        ProposalStatus to = event.to();
        if (to == ProposalStatus.IN_REVIEW) {
            return Optional.of("CotizaIA: " + capitalize(quote) + " está lista para revisar. Total: " + total + ".");
        }
        if (to == ProposalStatus.NEGOTIATING) {
            return Optional.of("CotizaIA: " + event.clientName() + " quiere negociar " + quote + ".");
        }
        if (to == ProposalStatus.ACCEPTED) {
            return Optional.of("CotizaIA: ¡" + event.clientName() + " aceptó " + quote + "! Total: " + total + ".");
        }
        if (to == ProposalStatus.REJECTED) {
            return Optional.of("CotizaIA: " + event.clientName() + " rechazó " + quote + ".");
        }
        return Optional.empty();
    }

    private static String formatTotal(BigDecimal total) {
        if (total == null) {
            return "sin calcular";
        }
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"));
        format.setMaximumFractionDigits(0);
        return format.format(total);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
