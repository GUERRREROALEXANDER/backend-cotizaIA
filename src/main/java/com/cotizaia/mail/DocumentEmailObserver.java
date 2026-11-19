package com.cotizaia.mail;

import com.cotizaia.document.DocumentType;
import com.cotizaia.domain.AppUser;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalDocument;
import com.cotizaia.repository.AppUserRepository;
import com.cotizaia.repository.ProposalDocumentRepository;
import com.cotizaia.repository.ProposalRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Mails the proposal or contract PDF after a send or an acceptance commits (spec 16: Observer,
 * same shape as {@code KnowledgeObserver}).
 *
 * <p>Recipients: the authenticated user who performed the action, plus the client on file only when
 * {@code MAIL_SEND_TO_CLIENT=true}. The body carries what that user already sees on screen (client
 * name, total, deposit and its reference) and never a session token. Delivery failures are caught
 * and logged with the proposal id alone: a dead SMTP server must not undo a sent proposal or turn
 * the HTTP call into an error.</p>
 */
@Component
public class DocumentEmailObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentEmailObserver.class);

    private final ProposalRepository proposals;

    private final ProposalDocumentRepository documents;

    private final AppUserRepository users;

    private final EmailGateway gateway;

    private final MailProperties mail;

    public DocumentEmailObserver(ProposalRepository proposals, ProposalDocumentRepository documents,
            AppUserRepository users, EmailGateway gateway, MailProperties mail) {
        this.proposals = proposals;
        this.documents = documents;
        this.users = users;
        this.gateway = gateway;
        this.mail = mail;
    }

    /** Runs only once the send/accept transaction is durable, in a transaction of its own for the reads. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDocumentRequested(ProposalDocumentEmailEvent event) {
        try {
            deliver(event);
        } catch (RuntimeException exception) {
            LOGGER.warn("document email failed for proposal {} ({})", event.proposalId(),
                    exception.getClass().getSimpleName());
        }
    }

    private void deliver(ProposalDocumentEmailEvent event) {
        Optional<ProposalDocument> document =
                documents.findByProposalIdAndType(event.proposalId(), event.documentType());
        if (document.isEmpty()) {
            LOGGER.warn("no {} document to mail for proposal {}", event.documentType(), event.proposalId());
            return;
        }
        Proposal proposal = proposals.findById(event.proposalId()).orElse(null);
        if (proposal == null) {
            LOGGER.warn("no proposal to mail for id {}", event.proposalId());
            return;
        }
        List<String> recipients = recipients(event, proposal);
        if (recipients.isEmpty()) {
            LOGGER.warn("no recipient for proposal {} document email", event.proposalId());
            return;
        }
        ProposalDocument pdf = document.get();
        EmailMessage.Attachment attachment =
                new EmailMessage.Attachment(pdf.getFileName(), pdf.getContentType(), pdf.getContent());
        EmailMessage message = new EmailMessage(recipients.get(0), subject(event), text(event, proposal),
                List.of(attachment));
        for (String recipient : recipients) {
            try {
                gateway.send(new EmailMessage(recipient, message.subject(), message.text(),
                        message.attachments()));
            } catch (RuntimeException exception) {
                LOGGER.warn("document email failed for proposal {} ({})", event.proposalId(),
                        exception.getClass().getSimpleName());
            }
        }
    }

    /** Acting user first (they asked for the mail), then the client only when the flag allows it. */
    private List<String> recipients(ProposalDocumentEmailEvent event, Proposal proposal) {
        Set<String> addresses = new LinkedHashSet<>();
        if (event.actorUserId() != null) {
            users.findById(event.actorUserId())
                    .map(AppUser::getEmail)
                    .filter(address -> address != null && !address.isBlank())
                    .ifPresent(addresses::add);
        }
        if (mail.isSendToClient()) {
            String client = proposal.getBrief().getClient().getEmail();
            if (client != null && !client.isBlank()) {
                addresses.add(client);
            }
        }
        return new ArrayList<>(addresses);
    }

    private static String subject(ProposalDocumentEmailEvent event) {
        return event.documentType() == DocumentType.CONTRACT
                ? "Contrato emitido: PDF adjunto"
                : "Propuesta enviada: PDF adjunto";
    }

    private static String text(ProposalDocumentEmailEvent event, Proposal proposal) {
        String client = proposal.getBrief().getClient().getName();
        String total = money(proposal.getTotal());
        if (event.documentType() == DocumentType.CONTRACT) {
            return "Hola:\n\nEl cliente " + client + " aceptó la propuesta " + proposal.getId()
                    + ". Adjuntamos el contrato emitido en PDF.\n\n"
                    + "Depósito: " + money(event.depositAmount()) + "\n"
                    + "Referencia del pago: " + event.paymentReference() + "\n"
                    + "Valor total de la propuesta: " + total + "\n\n"
                    + "El depósito es simulado: no se movió dinero real.";
        }
        return "Hola:\n\nAdjuntamos el PDF de la propuesta " + proposal.getId() + " que acabas de enviar al "
                + "cliente " + client + ".\n\n"
                + "Valor total: " + total + "\n\n"
                + "Este es el documento que recibió el cliente.";
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "no registrado" : "$" + amount.toPlainString();
    }
}
