package com.cotizaia.domain.spec;

import java.util.List;
import java.util.Objects;

/**
 * Specification pattern (Evans and Fowler, Domain-Driven Design): one business rule as an object that answers
 * "does this candidate satisfy me?". Rules combine with {@link #and}, {@link #or} and {@link #not} into new rules,
 * so a policy such as "ready for approval" is assembled from small, separately testable pieces instead of one long
 * {@code if}.
 *
 * @param <T> the type of object the rule judges
 */
@FunctionalInterface
public interface Specification<T> {

    boolean isSatisfiedBy(T candidate);

    default Specification<T> and(Specification<T> other) {
        Objects.requireNonNull(other);
        return candidate -> isSatisfiedBy(candidate) && other.isSatisfiedBy(candidate);
    }

    default Specification<T> or(Specification<T> other) {
        Objects.requireNonNull(other);
        return candidate -> isSatisfiedBy(candidate) || other.isSatisfiedBy(candidate);
    }

    default Specification<T> not() {
        return candidate -> !isSatisfiedBy(candidate);
    }

    /** All rules must hold; an empty list is trivially satisfied. */
    static <T> Specification<T> allOf(List<? extends Specification<T>> rules) {
        List<? extends Specification<T>> copy = List.copyOf(rules);
        return candidate -> copy.stream().allMatch(rule -> rule.isSatisfiedBy(candidate));
    }
}
