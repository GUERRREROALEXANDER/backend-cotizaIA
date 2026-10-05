package com.cotizaia.service;

import com.cotizaia.domain.Agency;
import com.cotizaia.pricing.PricingModel;
import com.cotizaia.repository.AgencyRepository;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the agency's default pricing selection (project.txt section 3).
 * Existing quotations retain their captured model while subsequent analyses use this default.
 */
@Service
@Transactional
public class AgencyService {

    private final AgencyRepository agencies;

    public AgencyService(AgencyRepository agencies) {
        this.agencies = agencies;
    }

    @Transactional(readOnly = true)
    public PricingModel getPricingModel(Long agencyId) {
        return get(agencyId).getPricingModel();
    }

    public PricingModel updatePricingModel(Long agencyId, PricingModel model) {
        Agency agency = get(agencyId);
        agency.setPricingModel(model);
        return agency.getPricingModel();
    }

    private Agency get(Long id) {
        return agencies.findById(id).orElseThrow(() -> new NoSuchElementException("Agency not found: " + id));
    }
}
