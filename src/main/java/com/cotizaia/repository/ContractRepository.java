package com.cotizaia.repository;

import com.cotizaia.domain.Contract;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository pattern: a contract is looked up by its natural key, the proposal
 * it belongs to (at most one contract per proposal), and scoped by agency so
 * one agency can never open another's contract.
 */
public interface ContractRepository extends JpaRepository<Contract, Long> {

    Optional<Contract> findByProposalId(Long proposalId);

    Optional<Contract> findByIdAndProposalBriefClientAgencyId(Long id, Long agencyId);
}
