package com.cotizaia.domain;

/**
 * Lifecycle of a {@link Contract} (project.txt section 4 acceptance flow;
 * section 9 schema: Propuesta -> Contrato). Stored in {@code contracts.status}
 * and constrained by {@code ck_contracts_status}.
 *
 * <p>Design pattern - <b>State</b>: each state owns the transitions it allows,
 * so an illegal change (e.g. issuing a contract twice) is rejected by the
 * domain rather than relying on callers. A contract may only move
 * {@code DRAFT -> ISSUED}, and {@code ISSUED} is terminal, which is exactly the
 * project's rule that the final contract is emitted once, on acceptance.
 */
public enum ContractStatus {
    DRAFT,
    ISSUED;

    /** True only for the one legal move: a draft becomes issued. */
    public boolean canTransitionTo(ContractStatus next) {
        return this == DRAFT && next == ISSUED;
    }
}
