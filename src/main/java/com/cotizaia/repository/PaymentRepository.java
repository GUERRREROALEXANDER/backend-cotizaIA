package com.cotizaia.repository;

import com.cotizaia.domain.Payment;
import com.cotizaia.domain.PaymentKind;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Finds persisted demo deposits by proposal (project.txt section 4). */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByProposalId(Long proposalId);

    Optional<Payment> findByProposalIdAndKind(Long proposalId, PaymentKind kind);

    boolean existsByProposalIdAndKind(Long proposalId, PaymentKind kind);
}
