package com.cotizaia.mail;

import com.cotizaia.document.DocumentType;
import java.math.BigDecimal;

/**
 * Event published inside the send/accept transaction and delivered to {@link DocumentEmailObserver}
 * only after that transaction commits (spec 16: Observer). It carries the identity of the person who
 * performed the action, because that person, not the service, is the recipient, plus the receipt
 * facts of an acceptance.
 *
 * @param proposalId       proposal whose document is mailed; also the only identifier ever logged on failure
 * @param actorUserId      authenticated user who performed the action, or null for a scripted call
 * @param documentType     PROPOSAL after a send, CONTRACT after an acceptance
 * @param depositAmount    simulated deposit of an acceptance, null for a send
 * @param paymentReference provider reference of that deposit, null for a send
 */
public record ProposalDocumentEmailEvent(Long proposalId, Long actorUserId, DocumentType documentType,
        BigDecimal depositAmount, String paymentReference) {

    public static ProposalDocumentEmailEvent sent(Long proposalId, Long actorUserId) {
        return new ProposalDocumentEmailEvent(proposalId, actorUserId, DocumentType.PROPOSAL, null, null);
    }

    public static ProposalDocumentEmailEvent accepted(Long proposalId, Long actorUserId, BigDecimal depositAmount,
            String paymentReference) {
        return new ProposalDocumentEmailEvent(proposalId, actorUserId, DocumentType.CONTRACT, depositAmount,
                paymentReference);
    }
}
