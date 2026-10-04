package com.cotizaia.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Smart schedule of a {@link Proposal} (project.txt section 5 "Cronograma
 * inteligente"; section 9 schema: Propuesta -> Cronograma -> Fase -> Dependencia).
 *
 * <p>Given the proposal's estimated hours, each {@link Phase} consumes a share
 * and gets a duration in whole weeks; the phases are then placed on a week
 * timeline honouring their finish-to-start {@link PhaseDependency} edges. When
 * the human edits the quoted hours at approval time,
 * {@link Proposal#editHours} recomputes the total here and the change ripples
 * through {@link #recalculate}: every duration is re-derived and each downstream
 * phase shifts, so the timeline stays internally consistent.
 *
 * <p>Design pattern - <b>Graph</b> (a DAG of phases) recomputed by a
 * topological placement. The aggregate is the single writer of the derived
 * {@code weeks}/{@code startWeek}/{@code endWeek}, which is what makes a stored
 * timeline that disagrees with its hours impossible. A dependency that would
 * close a cycle is rejected in {@link #addDependency} before any edge is added,
 * because SQL cannot express acyclicity portably.
 */
@Entity
@Table(name = "schedules")
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false)
    private Proposal proposal;

    @Column(name = "total_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalHours;

    @Column(name = "hours_per_week", nullable = false, precision = 6, scale = 2)
    private BigDecimal hoursPerWeek;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Phase> phases = new ArrayList<>();

    protected Schedule() {
    }

    /**
     * Factory method on the aggregate: a schedule never exists without its
     * proposal, and the owning side of the association is set here.
     */
    public static Schedule forProposal(Proposal proposal, BigDecimal hoursPerWeek) {
        if (proposal == null) {
            throw new IllegalArgumentException("proposal must not be null");
        }
        if (hoursPerWeek == null || hoursPerWeek.signum() <= 0) {
            throw new IllegalArgumentException("hoursPerWeek must be positive");
        }
        return new Schedule(proposal, hoursPerWeek);
    }

    private Schedule(Proposal proposal, BigDecimal hoursPerWeek) {
        this.proposal = proposal;
        this.hoursPerWeek = money(hoursPerWeek);
        this.totalHours = money(proposal.getTotalQuotedHours());
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Appends a phase with its share of the project hours and immediately
     * re-places the whole timeline, so the schedule is never momentarily
     * inconsistent with its phases. Phases may not consume more than the whole
     * project: a share that would push the sum of shares past 1 is rejected
     * before anything is written, so a rejected add leaves the schedule untouched.
     */
    public Phase addPhase(String name, BigDecimal hoursShare) {
        BigDecimal totalShare = hoursShare == null ? BigDecimal.ZERO : hoursShare;
        for (Phase phase : phases) {
            totalShare = totalShare.add(phase.getHoursShare());
        }
        if (totalShare.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException(
                    "Phases must not consume more than the project hours: adding \""
                            + name + "\" would raise the total share to " + totalShare
                            + ", exceeding 1");
        }
        Phase phase = new Phase(this, name, hoursShare, phases.size());
        phases.add(phase);
        recalculate();
        return phase;
    }

    /** Drops a phase (and its edges) and re-places the remaining timeline. */
    public void removePhase(Phase phase) {
        if (!phases.remove(phase)) {
            return;
        }
        // Remove edges pointing at the dropped phase from every other phase.
        for (Phase other : phases) {
            other.getDependencies().removeIf(edge -> edge.getDependsOnPhase() == phase);
        }
        phase.detach();
        recalculate();
    }

    /**
     * Adds a finish-to-start edge {@code dependent -> prerequisite}, rejecting
     * self-dependencies and any edge that would close a cycle. The check runs
     * before the edge is built, so a rejected graph is left untouched.
     */
    public PhaseDependency addDependency(Phase dependent, Phase prerequisite) {
        if (dependent == null || prerequisite == null) {
            throw new IllegalArgumentException("both phases must not be null");
        }
        if (dependent == prerequisite) {
            throw new IllegalArgumentException("a phase cannot depend on itself");
        }
        if (!phases.contains(dependent) || !phases.contains(prerequisite)) {
            throw new IllegalArgumentException("both phases must belong to this schedule");
        }
        if (createsCycle(dependent, prerequisite)) {
            throw new IllegalStateException(
                    "Dependency would create a cycle: " + dependent.getName()
                            + " -> " + prerequisite.getName());
        }
        PhaseDependency edge = new PhaseDependency(dependent, prerequisite);
        dependent.addDependencyEdge(edge);
        recalculate();
        return edge;
    }

    /** Drops an edge and re-places the timeline. */
    public void removeDependency(PhaseDependency edge) {
        if (edge == null) {
            return;
        }
        if (edge.getPhase().removeDependencyEdge(edge)) {
            recalculate();
        }
    }

    /**
     * Re-derives the whole schedule from the proposal's current hours and the
     * dependency graph: refreshes the total, computes each phase duration and
     * re-places each phase after its prerequisites. Called on every structural
     * change and, decisively, whenever the human edits quoted hours.
     */
    public void recalculate() {
        this.totalHours = money(proposal.getTotalQuotedHours());
        for (Phase phase : phases) {
            phase.applyPlacement(durationWeeks(phase), 1, 1);
        }
        placeInDependencyOrder();
    }

    /**
     * A phase lasts the whole weeks needed for its share of the total hours at
     * the planned weekly capacity, rounded up and never below one week.
     */
    private int durationWeeks(Phase phase) {
        BigDecimal phaseHours = totalHours.multiply(phase.getHoursShare());
        if (phaseHours.signum() <= 0) {
            return 1;
        }
        return phaseHours
                .divide(hoursPerWeek, 0, RoundingMode.CEILING)
                .max(BigDecimal.ONE)
                .intValueExact();
    }

    /**
     * Places each phase at the first week free of its prerequisites; a phase
     * with no prerequisites starts at week 1. Reuses the same topological order
     * as cycle detection, so the graph is already known to be acyclic here.
     */
    private void placeInDependencyOrder() {
        Map<Phase, Integer> indegree = new HashMap<>();
        for (Phase phase : phases) {
            indegree.put(phase, phase.getDependencies().size());
        }
        List<Phase> ready = new ArrayList<>();
        for (Phase phase : phases) {
            if (indegree.get(phase) == 0) {
                ready.add(phase);
            }
        }
        // Deterministic order: cycle check already failed for cyclic graphs.
        ready.sort(Comparator.comparingInt(Phase::getOrder));

        int placed = 0;
        while (!ready.isEmpty()) {
            Phase phase = ready.remove(0);
            placed++;
            int start = 1;
            for (PhaseDependency edge : phase.getDependencies()) {
                start = Math.max(start, edge.getDependsOnPhase().getEndWeek() + 1);
            }
            phase.applyPlacement(phase.getWeeks(), start, start + phase.getWeeks() - 1);

            for (Phase other : phases) {
                for (PhaseDependency edge : other.getDependencies()) {
                    if (edge.getDependsOnPhase() == phase) {
                        int remaining = indegree.merge(other, -1, Integer::sum);
                        if (remaining == 0) {
                            ready.add(other);
                        }
                    }
                }
            }
            ready.sort(Comparator.comparingInt(Phase::getOrder));
        }
        if (placed != phases.size()) {
            throw new IllegalStateException("Phase graph contains a cycle");
        }
    }

    /**
     * True when making {@code dependent} wait for {@code prerequisite} would
     * close a cycle, i.e. {@code prerequisite} already (transitively) depends on
     * {@code dependent}. A breadth-first walk over the existing edges.
     */
    private boolean createsCycle(Phase dependent, Phase prerequisite) {
        Set<Phase> visited = new HashSet<>();
        Deque<Phase> queue = new ArrayDeque<>();
        queue.add(prerequisite);
        while (!queue.isEmpty()) {
            Phase current = queue.poll();
            if (current == dependent) {
                return true;
            }
            if (!visited.add(current)) {
                continue;
            }
            for (PhaseDependency edge : current.getDependencies()) {
                queue.add(edge.getDependsOnPhase());
            }
        }
        return false;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Detaches the schedule when its proposal replaces it. */
    void detach() {
        this.proposal = null;
    }

    public Long getId() {
        return id;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public BigDecimal getTotalHours() {
        return totalHours;
    }

    public BigDecimal getHoursPerWeek() {
        return hoursPerWeek;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Unmodifiable view: phases are appended through {@link #addPhase}. */
    public List<Phase> getPhases() {
        return Collections.unmodifiableList(phases);
    }
}
