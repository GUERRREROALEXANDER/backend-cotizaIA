package com.cotizaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One finish-to-start edge of the schedule graph: {@code phase} cannot start
 * until {@code dependsOnPhase} ends. It is a composed child of the dependent
 * {@link Phase}, so it only exists through {@link Schedule#addDependency}.
 *
 * <p>Keeping the edge as a row (project.txt section 9: Cronograma -> Fase ->
 * Dependencia) is what lets the schedule re-place phases in cascade after an
 * hour edit. The {@link Schedule} aggregate validates acyclicity and
 * self-dependency before this row is built, because SQL cannot express those
 * constraints portably.
 */
@Entity
@Table(name = "phase_dependencies")
public class PhaseDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The dependent (later) phase that owns this edge. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "phase_id", nullable = false)
    private Phase phase;

    /** The prerequisite (earlier) phase this one waits for. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = jakarta.persistence.CascadeType.PERSIST)
    @JoinColumn(name = "depends_on_phase_id", nullable = false)
    private Phase dependsOnPhase;

    protected PhaseDependency() {
    }

    PhaseDependency(Phase phase, Phase dependsOnPhase) {
        if (phase == null || dependsOnPhase == null) {
            throw new IllegalArgumentException("both phases must not be null");
        }
        if (phase == dependsOnPhase) {
            throw new IllegalArgumentException("a phase cannot depend on itself");
        }
        this.phase = phase;
        this.dependsOnPhase = dependsOnPhase;
    }

    public Long getId() {
        return id;
    }

    public Phase getPhase() {
        return phase;
    }

    public Phase getDependsOnPhase() {
        return dependsOnPhase;
    }
}
