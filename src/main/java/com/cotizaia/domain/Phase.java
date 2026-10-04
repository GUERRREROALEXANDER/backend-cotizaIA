package com.cotizaia.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One phase of a {@link Schedule} (project.txt section 5 "Cronograma
 * inteligente": Design / Development / Integration / QA; section 9: Fase).
 * It holds the fraction of project hours it consumes and the duration and
 * timeline placement the schedule derives from it.
 *
 * <p>The phase is a composed child: it is built by {@link Schedule#addPhase}
 * and its derived fields ({@code weeks}, {@code startWeek}, {@code endWeek}) are
 * written only by {@link Schedule#recalculate} through
 * {@link #applyPlacement}. There are no public setters for them, so a stored
 * duration cannot drift from the hours that produced it.
 */
@Entity
@Table(name = "phases")
public class Phase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "hours_share", nullable = false, precision = 6, scale = 4)
    private java.math.BigDecimal hoursShare;

    /** Derived duration in whole weeks (never zero: any shared hours take a week). */
    @Column(nullable = false)
    private int weeks;

    /** Planned sequence, used as the deterministic tie-break for the layout. */
    @Column(name = "phase_order", nullable = false)
    private int order;

    /** Derived timeline placement, shifted in cascade with upstream durations. */
    @Column(name = "start_week", nullable = false)
    private int startWeek;

    @Column(name = "end_week", nullable = false)
    private int endWeek;

    /** Edges owned by this phase; the dependent side of the graph. */
    @OneToMany(mappedBy = "phase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PhaseDependency> dependencies = new ArrayList<>();

    protected Phase() {
    }

    Phase(Schedule schedule, String name, java.math.BigDecimal hoursShare, int order) {
        if (schedule == null) {
            throw new IllegalArgumentException("schedule must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (hoursShare == null || hoursShare.signum() <= 0
                || hoursShare.compareTo(java.math.BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("hoursShare must be within (0, 1]");
        }
        if (order < 0) {
            throw new IllegalArgumentException("order must not be negative");
        }
        this.schedule = schedule;
        this.name = name;
        this.hoursShare = hoursShare;
        this.order = order;
    }

    /**
     * Writes the placement derived by {@link Schedule#recalculate}. Package
     * private so only the aggregate can move a phase, keeping {@code weeks} and
     * the start/end range consistent with the hours.
     */
    void applyPlacement(int weeks, int startWeek, int endWeek) {
        this.weeks = weeks;
        this.startWeek = startWeek;
        this.endWeek = endWeek;
    }

    /** Detaches the phase when its schedule drops it, keeping both sides in sync. */
    void detach() {
        this.schedule = null;
    }

    public Long getId() {
        return id;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public String getName() {
        return name;
    }

    public java.math.BigDecimal getHoursShare() {
        return hoursShare;
    }

    public int getWeeks() {
        return weeks;
    }

    public int getOrder() {
        return order;
    }

    public int getStartWeek() {
        return startWeek;
    }

    public int getEndWeek() {
        return endWeek;
    }

    /** Unmodifiable view: edges are added through {@link Schedule#addDependency}. */
    public List<PhaseDependency> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    /** Package-private add used by {@link Schedule#addDependency}. */
    void addDependencyEdge(PhaseDependency dependency) {
        dependencies.add(dependency);
    }

    /** Package-private removal used by the aggregate when an edge is dropped. */
    boolean removeDependencyEdge(PhaseDependency dependency) {
        return dependencies.remove(dependency);
    }
}
