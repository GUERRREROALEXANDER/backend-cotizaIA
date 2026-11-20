package com.cotizaia.domain.spec;

import java.util.Objects;

/**
 * A specification with a stable code and a human description, so a report can say which rule failed and why.
 * It is itself a {@link Specification} and keeps combining with {@code and / or / not}.
 */
public record NamedRule<T>(String code, String description, Specification<T> rule) implements Specification<T> {

    public NamedRule {
        Objects.requireNonNull(code);
        Objects.requireNonNull(description);
        Objects.requireNonNull(rule);
    }

    @Override
    public boolean isSatisfiedBy(T candidate) {
        return rule.isSatisfiedBy(candidate);
    }
}
