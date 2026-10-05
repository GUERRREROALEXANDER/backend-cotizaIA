package com.cotizaia.service;

import com.cotizaia.domain.RequirementType;
import com.cotizaia.domain.ServiceCatalog;
import com.cotizaia.domain.ServiceCategory;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.ServiceCatalogRepository;
import com.cotizaia.repository.ServiceCategoryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains agency catalogs through their aggregate methods (project.txt sections 2 and 9).
 * Children are changed through their owning catalog so orphan removal and reference constraints remain effective.
 */
@Service
@Transactional
public class CatalogService {

    private final ServiceCatalogRepository catalogs;

    private final AgencyRepository agencies;

    private final ServiceCategoryRepository categories;

    public CatalogService(ServiceCatalogRepository catalogs, AgencyRepository agencies,
            ServiceCategoryRepository categories) {
        this.catalogs = catalogs;
        this.agencies = agencies;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<ServiceCatalog> list(Long agencyId) {
        List<ServiceCatalog> result = catalogs.findByAgencyIdOrderByIdAsc(agencyId);
        result.forEach(catalog -> catalog.getRequirementTypes().size());
        return result;
    }

    private ServiceCatalog get(Long agencyId, Long id) {
        return catalogs.findByIdAndAgencyId(id, agencyId)
                .orElseThrow(() -> new NoSuchElementException("Catalog not found: " + id));
    }

    public ServiceCatalog create(Long agencyId, String name, String description, Long categoryId) {
        ServiceCatalog catalog = new ServiceCatalog(agencies.findById(agencyId)
                .orElseThrow(() -> new NoSuchElementException("Agency not found: " + agencyId)), name);
        catalog.setDescription(description);
        catalog.setCategory(category(agencyId, categoryId));
        return catalogs.saveAndFlush(catalog);
    }

    public ServiceCatalog update(Long agencyId, Long id, String name, String description, Long categoryId) {
        ServiceCatalog catalog = get(agencyId, id);
        catalog.setName(name);
        catalog.setDescription(description);
        catalog.setCategory(category(agencyId, categoryId));
        catalog.getRequirementTypes().size();
        return catalogs.saveAndFlush(catalog);
    }

    public void delete(Long agencyId, Long id) {
        catalogs.delete(get(agencyId, id));
        catalogs.flush();
    }

    public RequirementType addType(Long agencyId, Long catalogId, String name, String description,
            BigDecimal estimatedHours) {
        ServiceCatalog catalog = get(agencyId, catalogId);
        RequirementType type = catalog.addRequirementType(name, description, estimatedHours);
        catalogs.saveAndFlush(catalog);
        return type;
    }

    public RequirementType updateType(Long agencyId, Long catalogId, Long typeId, String name,
            String description, BigDecimal estimatedHours) {
        RequirementType type = type(get(agencyId, catalogId), typeId);
        type.setName(name);
        type.setDescription(description);
        type.setEstimatedHours(estimatedHours);
        catalogs.flush();
        return type;
    }

    public void deleteType(Long agencyId, Long catalogId, Long typeId) {
        ServiceCatalog catalog = get(agencyId, catalogId);
        catalog.removeRequirementType(type(catalog, typeId));
        catalogs.flush();
    }

    private RequirementType type(ServiceCatalog catalog, Long id) {
        return catalog.getRequirementTypes().stream().filter(type -> type.getId().equals(id)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Requirement type not found: " + id));
    }

    private ServiceCategory category(Long agencyId, Long id) {
        if (id == null) {
            return null;
        }
        return categories.findByIdAndAgencyId(id, agencyId)
                .orElseThrow(() -> new NoSuchElementException("Category not found: " + id));
    }
}
