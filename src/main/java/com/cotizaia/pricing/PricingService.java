package com.cotizaia.pricing;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Role;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.RoleRepository;
import com.cotizaia.service.RateService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Selects the agency's pricing model and derives a rate from its current roles
 * (project.txt section 2). The service never asks AI to choose a final price;
 * humans approve the resulting provisional quote.
 *
 * <p>Design pattern - <b>Strategy</b>: this Context delegates calculation to
 * {@link PricingStrategy} through {@link PricingStrategyResolver}.
 */
@Service
public class PricingService {

    private final AgencyRepository agencyRepository;
    private final RoleRepository roleRepository;
    private final RateService rateService;
    private final PricingStrategyResolver resolver;

    public PricingService(AgencyRepository agencyRepository, RoleRepository roleRepository, RateService rateService,
            PricingStrategyResolver resolver) {
        this.agencyRepository = agencyRepository;
        this.roleRepository = roleRepository;
        this.rateService = rateService;
        this.resolver = resolver;
    }

    /**
     * Read-only, so a missing-rate failure must not mark the caller's transaction rollback-only: the agent's
     * run recorder has to commit the FAILED log after this exception.
     */
    @Transactional(readOnly = true, noRollbackFor = NoSuchElementException.class)
    public PricingQuote quote(Long agencyId, PricingModel modelOrNull, List<PricingLine> lines) {
        Agency agency = agencyRepository.findById(agencyId)
                .orElseThrow(() -> new NoSuchElementException("Agency not found: " + agencyId));
        PricingModel model = modelOrNull == null ? agency.getPricingModel() : modelOrNull;
        BigDecimal hourlyRate = BigDecimal.valueOf(blendedRate(agencyId, Instant.now()));
        return resolver.forModel(model).price(new PricingRequest(lines, hourlyRate));
    }

    @Transactional(readOnly = true, noRollbackFor = NoSuchElementException.class)
    public long blendedRate(Long agencyId, Instant at) {
        List<Long> rates = roleRepository.findByAgencyIdOrderByIdAsc(agencyId).stream()
                .map(Role::getId)
                .map(roleId -> rateService.currentRate(agencyId, roleId, at))
                .flatMap(Optional::stream)
                .toList();
        if (rates.isEmpty()) {
            throw new NoSuchElementException("Agency " + agencyId + " has no valid rates configured");
        }
        BigDecimal total = rates.stream()
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(rates.size()), 0, RoundingMode.HALF_UP).longValueExact();
    }
}
