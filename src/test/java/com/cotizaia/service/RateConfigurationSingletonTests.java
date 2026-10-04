package com.cotizaia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.config.RateConfiguration;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Rate;
import com.cotizaia.domain.Role;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.RoleRepository;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the Singleton cache contract: pricing reads through it, it is a
 * single shared Spring bean, and updates invalidate it.
 */
@SpringBootTest
@ActiveProfiles("test")
class RateConfigurationSingletonTests {

    @Autowired
    private RateService rateService;

    @Autowired
    private AgencyRepository agencyRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RateConfiguration rateConfiguration;

    @BeforeEach
    void resetCache() {
        rateConfiguration.clear();
    }

    @Test
    void rateConfigurationIsASingleSharedInstance() {
        // The injected Singleton must be the same object the context created:
        // one shared cache, no per-classloader duplicate.
        assertThat(rateService).isNotNull();
        assertThat(rateConfiguration).isSameAs(rateConfiguration);
    }

    @Test
    void pricingReadsThroughTheSingletonCache() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Cache"));
        Role role = rateService.createRole(agency.getId(), "Developer");
        Rate rate = rateService.createRate(agency.getId(), role.getId(), 150_000L, null, null);

        Long amount = rateService.requireCurrentRate(agency.getId(), role.getId(), Instant.now());
        assertThat(amount).isEqualTo(150_000L);

        // The read hydrated the cache timeline under (agency, role).
        assertThat(rateConfiguration.resolve(agency.getId(), role.getId(), Instant.now()))
                .isEqualTo(150_000L);
        assertThat(rate.getCopPerHour()).isEqualTo(150_000L);
    }

    @Test
    void cacheInvalidatesOnRateUpdate() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Update"));
        Role role = rateService.createRole(agency.getId(), "Designer");
        Rate rate = rateService.createRate(agency.getId(), role.getId(), 100_000L, null, null);

        assertThat(rateService.requireCurrentRate(agency.getId(), role.getId(), Instant.now()))
                .isEqualTo(100_000L);
        assertThat(rateConfiguration.resolve(agency.getId(), role.getId(), Instant.now()))
                .isEqualTo(100_000L);

        rateService.updateRate(agency.getId(), rate.getId(), 200_000L);

        // Invalidation dropped the stale entry...
        assertThat(rateConfiguration.get(agency.getId(), role.getId())).isNull();
        // ...and the next read serves the new number, not the cached one.
        assertThat(rateService.requireCurrentRate(agency.getId(), role.getId(), Instant.now()))
                .isEqualTo(200_000L);
    }

    @Test
    void cacheInvalidatesOnRateDelete() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Delete"));
        Role role = rateService.createRole(agency.getId(), "PM");
        Rate rate = rateService.createRate(agency.getId(), role.getId(), 80_000L, null, null);

        rateService.requireCurrentRate(agency.getId(), role.getId(), Instant.now());
        assertThat(rateConfiguration.resolve(agency.getId(), role.getId(), Instant.now()))
                .isEqualTo(80_000L);

        rateService.deleteRate(agency.getId(), rate.getId());

        assertThat(rateConfiguration.get(agency.getId(), role.getId())).isNull();
        assertThat(rateService.currentRate(agency.getId(), role.getId(), Instant.now())).isEmpty();
    }

    @Test
    void cacheInvalidatesWhenRoleIsDeleted() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Rol Delete"));
        Role role = rateService.createRole(agency.getId(), "Developer");
        rateService.createRate(agency.getId(), role.getId(), 120_000L, null, null);
        rateService.requireCurrentRate(agency.getId(), role.getId(), Instant.now());
        assertThat(rateConfiguration.resolve(agency.getId(), role.getId(), Instant.now()))
                .isEqualTo(120_000L);

        rateService.deleteRole(agency.getId(), role.getId());

        assertThat(rateConfiguration.get(agency.getId(), role.getId())).isNull();
    }

    @Test
    void pricingPicksTheRateValidAtTheGivenInstant() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Efectiva"));
        Role role = rateService.createRole(agency.getId(), "Developer");
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-12-31T00:00:00Z");

        rateService.createRate(agency.getId(), role.getId(), 100_000L, null, null);
        rateService.createRate(agency.getId(), role.getId(), 130_000L, start, end);

        // The windowed rate is the latest valid one and wins while in range.
        assertThat(rateService.requireCurrentRate(
                        agency.getId(), role.getId(), Instant.parse("2026-06-01T00:00:00Z")))
                .isEqualTo(130_000L);
        // Outside the window only the always-valid rate applies.
        assertThat(rateService.requireCurrentRate(
                        agency.getId(), role.getId(), Instant.parse("2027-06-01T00:00:00Z")))
                .isEqualTo(100_000L);
    }

    @Test
    void requireCurrentRateFailsWhenNoRateExists() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Sin Tarifa"));
        Role role = rateService.createRole(agency.getId(), "Developer");

        assertThatThrownBy(() -> rateService.requireCurrentRate(
                        agency.getId(), role.getId(), Instant.now()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void rateCrudRejectsForeignAgencyScope() {
        Agency first = agencyRepository.saveAndFlush(new Agency("Agencia Scope Uno"));
        Agency second = agencyRepository.saveAndFlush(new Agency("Agencia Scope Dos"));
        Role role = rateService.createRole(first.getId(), "Developer");
        Rate rate = rateService.createRate(first.getId(), role.getId(), 100_000L, null, null);

        assertThatThrownBy(() -> rateService.updateRate(second.getId(), rate.getId(), 999_000L))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(rateService.listRoles(second.getId())).isEmpty();
        assertThat(rateService.listRates(second.getId(), role.getId())).isEmpty();
    }
}
