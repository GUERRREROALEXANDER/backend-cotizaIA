package com.cotizaia.service;

import com.cotizaia.config.RateConfiguration;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Rate;
import com.cotizaia.domain.Role;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.RateRepository;
import com.cotizaia.repository.RoleRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rate CRUD per role plus the pricing read path.
 *
 * <p>The service is the only place that writes {@link Rate}s, which makes it
 * the natural owner of cache invalidation: every mutation drops the affected
 * key in the {@link RateConfiguration} GoF Singleton
 * (the injected bean published by {@code RateConfigurationBeans} is
 * {@link RateConfiguration#getInstance()}), so {@link #currentRate} can
 * safely be read-through. Pricing never reads {@link RateRepository} directly
 * (acceptance criterion), it always resolves through the Singleton cache.
 *
 * <p>Amounts are COP whole numbers ({@code long}), matching the domain rule
 * documented in {@link Rate}.
 */
@Service
public class RateService {

    private final AgencyRepository agencyRepository;
    private final RoleRepository roleRepository;
    private final RateRepository rateRepository;
    private final RateConfiguration rateConfiguration;

    public RateService(
            AgencyRepository agencyRepository,
            RoleRepository roleRepository,
            RateRepository rateRepository,
            RateConfiguration rateConfiguration) {
        this.agencyRepository = agencyRepository;
        this.roleRepository = roleRepository;
        this.rateRepository = rateRepository;
        this.rateConfiguration = rateConfiguration;
    }

    // --- Role CRUD ---------------------------------------------------------

    @Transactional
    public Role createRole(Long agencyId, String name) {
        if (roleRepository.existsByAgencyIdAndName(agencyId, name)) {
            throw new IllegalStateException("Role already exists for agency: " + name);
        }
        Agency agency = agencyRepository
                .findById(agencyId)
                .orElseThrow(() -> new NoSuchElementException("Agency not found: " + agencyId));
        return roleRepository.save(new Role(agency, name));
    }

    @Transactional(readOnly = true)
    public List<Role> listRoles(Long agencyId) {
        return roleRepository.findByAgencyIdOrderByIdAsc(agencyId);
    }

    @Transactional
    public void deleteRole(Long agencyId, Long roleId) {
        Role role = roleRepository
                .findByIdAndAgencyId(roleId, agencyId)
                .orElseThrow(() -> new NoSuchElementException("Role not found: " + roleId));
        roleRepository.delete(role);
        // Bulk delete removed every rate for this role: drop the whole agency
        // slice so no orphan cache entry survives.
        rateConfiguration.invalidateAgency(agencyId);
    }

    // --- Rate CRUD ---------------------------------------------------------

    /**
     * Creates a rate for a role. Effective dating is optional: pass nulls for
     * an always-valid rate or a window for historical/temporal pricing.
     */
    @Transactional
    public Rate createRate(Long agencyId, Long roleId, long copPerHour, Instant effectiveFrom, Instant effectiveTo) {
        Role role = roleRepository
                .findByIdAndAgencyId(roleId, agencyId)
                .orElseThrow(() -> new NoSuchElementException("Role not found: " + roleId));
        Rate rate = role.addRate(copPerHour);
        rate.setEffectiveFrom(effectiveFrom);
        rate.setEffectiveTo(effectiveTo);
        Rate saved = rateRepository.save(rate);
        rateConfiguration.invalidate(agencyId, roleId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Rate> listRates(Long agencyId, Long roleId) {
        return rateRepository.findByRoleIdOrderByIdAsc(roleId).stream()
                .filter(rate -> rate.getAgency().getId().equals(agencyId))
                .toList();
    }

    @Transactional
    public Rate updateRate(Long agencyId, Long rateId, long copPerHour) {
        Rate rate = rateRepository
                .findByIdAndAgencyId(rateId, agencyId)
                .orElseThrow(() -> new NoSuchElementException("Rate not found: " + rateId));
        rate.setCopPerHour(copPerHour);
        Rate saved = rateRepository.save(rate);
        // Acceptance: cache invalidates on rate update.
        rateConfiguration.invalidate(agencyId, rate.getRole().getId());
        return saved;
    }

    @Transactional
    public void deleteRate(Long agencyId, Long rateId) {
        Rate rate = rateRepository
                .findByIdAndAgencyId(rateId, agencyId)
                .orElseThrow(() -> new NoSuchElementException("Rate not found: " + rateId));
        Long roleId = rate.getRole().getId();
        rateRepository.delete(rate);
        rateConfiguration.invalidate(agencyId, roleId);
    }

    // --- Pricing read path through the Singleton ---------------------------

    /**
     * Resolves the rate valid at {@code at} for a role, reading through the
     * {@link RateConfiguration} GoF Singleton ({@link RateConfiguration#getInstance()}). If the cache misses, it loads the
     * whole rate timeline from the repository and caches it; an update
     * invalidates that key, so the next read picks the new number. Effective
     * selection runs on the cached timeline, which keeps one cache entry per
     * role while still honouring effective dating.
     *
     * @return the COP/hour amount, or {@code Optional.empty()} when the role
     *         has no rate valid at that instant.
     */
    @Transactional(readOnly = true)
    public Optional<Long> currentRate(Long agencyId, Long roleId, Instant at) {
        Long cached = rateConfiguration.resolve(agencyId, roleId, at);
        if (cached != null) {
            return Optional.of(cached);
        }

        if (rateConfiguration.get(agencyId, roleId) == null) {
            rateConfiguration.putTimeline(agencyId, roleId, loadTimeline(agencyId, roleId));
        }
        return Optional.ofNullable(rateConfiguration.resolve(agencyId, roleId, at));
    }

    private List<RateConfiguration.CachedRate> loadTimeline(Long agencyId, Long roleId) {
        return rateRepository.findByRoleIdOrderByIdAsc(roleId).stream()
                .filter(rate -> rate.getAgency().getId().equals(agencyId))
                .map(rate -> new RateConfiguration.CachedRate(
                        rate.getCopPerHour(), rate.getEffectiveFrom(), rate.getEffectiveTo()))
                .toList();
    }

    /**
     * Pricing entry point: the rate must exist, otherwise the pipeline cannot
     * quote. Kept separate from {@link #currentRate} so callers choose between
     * "maybe" and "must".
     */
    @Transactional(readOnly = true)
    public long requireCurrentRate(Long agencyId, Long roleId, Instant at) {
        return currentRate(agencyId, roleId, at)
                .orElseThrow(() -> new NoSuchElementException(
                        "No valid rate for role " + roleId + " at " + at));
    }
}
