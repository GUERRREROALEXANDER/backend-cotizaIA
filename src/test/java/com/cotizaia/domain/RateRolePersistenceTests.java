package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.RateRepository;
import com.cotizaia.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RateRolePersistenceTests {

    @Autowired
    private AgencyRepository agencyRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RateRepository rateRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsRoleWithNestedRatesInCop() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Estudio Tarifas"));
        Role role = new Role(agency, "Developer");
        role.addRate(120_000L);
        Role saved = roleRepository.saveAndFlush(role);
        entityManager.refresh(saved);

        assertThat(saved.getId()).isNotNull();
        assertThat(roleRepository.findByAgencyIdOrderByIdAsc(agency.getId()))
                .extracting(Role::getName)
                .containsExactly("Developer");
        assertThat(saved.getRates()).hasSize(1);
        assertThat(saved.getRates().get(0).getCopPerHour()).isEqualTo(120_000L);
        assertThat(saved.getRates().get(0).getAgency().getId()).isEqualTo(agency.getId());
    }

    @Test
    void keepsRatesAndRolesScopedToTheirAgency() {
        Agency first = agencyRepository.saveAndFlush(new Agency("Agencia COP Uno"));
        Agency second = agencyRepository.saveAndFlush(new Agency("Agencia COP Dos"));

        Role designerFirst = roleRepository.saveAndFlush(new Role(first, "Designer"));
        roleRepository.saveAndFlush(new Role(second, "Designer"));

        roleRepository.saveAndFlush(new Role(first, "PM"));

        assertThat(roleRepository.findByAgencyIdOrderByIdAsc(first.getId()))
                .extracting(Role::getName)
                .containsExactly("Designer", "PM");
        // Same role name in two agencies is allowed; the unique key is (agency_id, name).
        assertThat(roleRepository.findByAgencyIdAndName(first.getId(), "Designer")).isPresent();
        assertThat(roleRepository.findByAgencyIdAndName(second.getId(), "Designer")).isPresent();
        assertThat(roleRepository.findByIdAndAgencyId(designerFirst.getId(), second.getId())).isEmpty();
    }

    @Test
    void rejectsDuplicateRoleNameWithinAgency() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Rol Duplicado"));
        roleRepository.saveAndFlush(new Role(agency, "Developer"));

        assertThatThrownBy(() -> roleRepository.saveAndFlush(new Role(agency, "Developer")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNonPositiveCopRateInDomain() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Invalida"));
        Role role = new Role(agency, "Developer");

        assertThatThrownBy(() -> role.addRate(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> role.addRate(-5L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveCopRateByDatabaseCheck() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Check"));
        Role role = roleRepository.saveAndFlush(new Role(agency, "Developer"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO rates (agency_id, role_id, cop_per_hour, created_at)"
                        + " VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                agency.getId(), role.getId(), 0L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsRateWithUnknownAgencyByForeignKey() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia FK"));
        Role role = roleRepository.saveAndFlush(new Role(agency, "Developer"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO rates (agency_id, role_id, cop_per_hour, created_at)"
                        + " VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                -1L, role.getId(), 100_000L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsRateWithUnknownRoleByForeignKey() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia FK Rol"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO rates (agency_id, role_id, cop_per_hour, created_at)"
                        + " VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                agency.getId(), -1L, 100_000L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void supportsOptionalEffectiveDating() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Fechas"));
        Role role = new Role(agency, "Developer");
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-12-31T00:00:00Z");

        Rate alwaysValid = role.addRate(100_000L);
        Rate windowed = role.addRate(110_000L);
        windowed.setEffectiveFrom(start);
        windowed.setEffectiveTo(end);
        Role saved = roleRepository.saveAndFlush(role);
        entityManager.refresh(saved);

        assertThat(saved.getRates()).hasSize(2);
        assertThat(alwaysValid.getEffectiveFrom()).isNull();
        assertThat(alwaysValid.getEffectiveTo()).isNull();
        assertThat(alwaysValid.isValidAt(Instant.parse("2030-01-01T00:00:00Z"))).isTrue();
        assertThat(windowed.isValidAt(Instant.parse("2026-06-01T00:00:00Z"))).isTrue();
        assertThat(windowed.isValidAt(Instant.parse("2027-06-01T00:00:00Z"))).isFalse();
    }

    @Test
    void orphanRemovalDeletesRateWhenDetachedFromRole() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Orphan Rate"));
        Role role = new Role(agency, "Developer");
        role.addRate(120_000L);
        role = roleRepository.saveAndFlush(role);
        Long roleId = role.getId();

        role.removeRate(role.getRates().get(0));
        roleRepository.saveAndFlush(role);

        assertThat(rateRepository.countByRoleId(roleId)).isZero();
    }

    @Test
    void deleteRoleCascadesToItsRates() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Cascada Rate"));
        Role role = new Role(agency, "PM");
        role.addRate(90_000L);
        role = roleRepository.saveAndFlush(role);
        Long roleId = role.getId();
        assertThat(rateRepository.countByRoleId(roleId)).isEqualTo(1);

        roleRepository.delete(role);
        roleRepository.flush();

        assertThat(roleRepository.findById(roleId)).isEmpty();
        assertThat(rateRepository.countByRoleId(roleId)).isZero();
    }
}
