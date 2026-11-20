package com.cotizaia.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class NamedRuleTests {

    private final NamedRule<Integer> positive = new NamedRule<>("POSITIVE", "Must be greater than zero", n -> n > 0);
    private final NamedRule<Integer> even = new NamedRule<>("EVEN", "Must be even", n -> n % 2 == 0);

    @Test
    void delegatesToTheWrappedRule() {
        assertThat(positive.isSatisfiedBy(3)).isTrue();
        assertThat(positive.isSatisfiedBy(-3)).isFalse();
    }

    @Test
    void keepsItsCodeAndDescriptionForReports() {
        assertThat(positive.code()).isEqualTo("POSITIVE");
        assertThat(positive.description()).isEqualTo("Must be greater than zero");
    }

    @Test
    void stillCombinesLikeAnySpecification() {
        Specification<Integer> positiveAndEven = positive.and(even);
        assertThat(positiveAndEven.isSatisfiedBy(4)).isTrue();
        assertThat(positiveAndEven.isSatisfiedBy(3)).isFalse();
        assertThat(positive.or(even).isSatisfiedBy(-2)).isTrue();
        assertThat(positive.not().isSatisfiedBy(-1)).isTrue();
        assertThat(Specification.allOf(List.of(positive, even)).isSatisfiedBy(6)).isTrue();
    }

    @Test
    void refusesMissingParts() {
        assertThatThrownBy(() -> new NamedRule<Integer>(null, "d", n -> true)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NamedRule<Integer>("c", null, n -> true)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NamedRule<Integer>("c", "d", null)).isInstanceOf(NullPointerException.class);
    }
}
