package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.RequirementTypeRepository;
import com.cotizaia.repository.ServiceCatalogRepository;
import com.cotizaia.repository.ServiceCategoryRepository;
import java.math.BigDecimal;
import java.util.List;
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
class ServiceCatalogPersistenceTests {

    @Autowired
    private AgencyRepository agencyRepository;

    @Autowired
    private ServiceCategoryRepository categoryRepository;

    @Autowired
    private ServiceCatalogRepository catalogRepository;

    @Autowired
    private RequirementTypeRepository requirementTypeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsCategoryCatalogAndNestedRequirementTypes() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Estudio Web"));
        ServiceCategory category =
                categoryRepository.saveAndFlush(new ServiceCategory(agency, "Web development"));

        ServiceCatalog catalog = new ServiceCatalog(agency, "Corporate website");
        catalog.setCategory(category);
        catalog.setDescription("Marketing site with CMS");
        catalog.addRequirementType("Responsive layout", "Mobile-first", new BigDecimal("16.00"));
        catalog.addRequirementType("Payment gateway", "Checkout integration", new BigDecimal("24.50"));
        ServiceCatalog saved = catalogRepository.saveAndFlush(catalog);

        assertThat(saved.getId()).isNotNull();
        assertThat(catalogRepository.findByAgencyIdOrderByIdAsc(agency.getId()))
                .extracting(ServiceCatalog::getName)
                .containsExactly("Corporate website");
        assertThat(catalogRepository.findByIdAndAgencyId(saved.getId(), agency.getId())).isPresent();

        List<RequirementType> types =
                requirementTypeRepository.findByServiceCatalogIdOrderByIdAsc(saved.getId());
        assertThat(types)
                .extracting(RequirementType::getName)
                .containsExactly("Responsive layout", "Payment gateway");
        assertThat(types.get(1).getEstimatedHours()).isEqualByComparingTo("24.50");
        assertThat(types)
                .extracting(type -> type.getServiceCatalog().getId())
                .containsOnly(saved.getId());
    }

    @Test
    void keepsCatalogAndCategoriesScopedToTheirAgency() {
        Agency first = agencyRepository.saveAndFlush(new Agency("Agencia Uno"));
        Agency second = agencyRepository.saveAndFlush(new Agency("Agencia Dos"));

        categoryRepository.saveAndFlush(new ServiceCategory(first, "Web development"));
        categoryRepository.saveAndFlush(new ServiceCategory(second, "Web development"));

        catalogRepository.saveAndFlush(new ServiceCatalog(first, "Web"));
        catalogRepository.saveAndFlush(new ServiceCatalog(second, "Web"));
        catalogRepository.saveAndFlush(new ServiceCatalog(first, "Branding"));

        assertThat(catalogRepository.findByAgencyIdOrderByIdAsc(first.getId()))
                .extracting(ServiceCatalog::getName)
                .containsExactly("Web", "Branding");
        assertThat(categoryRepository.findByAgencyIdOrderByIdAsc(second.getId())).hasSize(1);
        // Same name in two agencies is allowed; the unique key is (agency_id, name).
        assertThat(catalogRepository.findByAgencyIdAndName(first.getId(), "Web")).isPresent();
        assertThat(catalogRepository.findByAgencyIdAndName(second.getId(), "Web")).isPresent();
    }

    @Test
    void findsRequirementTypesByCatalogWithinAgency() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Scope"));
        ServiceCatalog catalog = catalogRepository.saveAndFlush(new ServiceCatalog(agency, "App"));
        catalog.addRequirementType("Onboarding", null, null);
        catalogRepository.saveAndFlush(catalog);

        assertThat(requirementTypeRepository
                        .findByServiceCatalogAgencyIdAndServiceCatalogIdOrderByIdAsc(
                                agency.getId(), catalog.getId()))
                .extracting(RequirementType::getName)
                .containsExactly("Onboarding");
    }

    @Test
    void rejectsDuplicateCatalogNameWithinAgency() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Duplicada"));
        catalogRepository.saveAndFlush(new ServiceCatalog(agency, "Branding"));

        assertThatThrownBy(() -> catalogRepository.saveAndFlush(new ServiceCatalog(agency, "Branding")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateCategoryNameWithinAgency() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Categorias"));
        categoryRepository.saveAndFlush(new ServiceCategory(agency, "Marketing"));

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(new ServiceCategory(agency, "Marketing")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateRequirementTypeNameWithinCatalog() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Requisitos"));
        ServiceCatalog catalog = catalogRepository.saveAndFlush(new ServiceCatalog(agency, "Web"));

        catalog.addRequirementType("Login", null, null);
        catalogRepository.saveAndFlush(catalog);

        catalog.addRequirementType("Login", "duplicated", null);

        assertThatThrownBy(() -> catalogRepository.saveAndFlush(catalog))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameRequirementTypeNameInDifferentCatalogs() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Reuso"));
        ServiceCatalog first = catalogRepository.saveAndFlush(new ServiceCatalog(agency, "Web"));
        ServiceCatalog second = catalogRepository.saveAndFlush(new ServiceCatalog(agency, "App"));

        first.addRequirementType("Login", null, null);
        second.addRequirementType("Login", "a different catalog may reuse the name", null);
        catalogRepository.saveAndFlush(first);
        catalogRepository.saveAndFlush(second);

        assertThat(requirementTypeRepository.existsByServiceCatalogIdAndName(first.getId(), "Login"))
                .isTrue();
        assertThat(requirementTypeRepository.existsByServiceCatalogIdAndName(second.getId(), "Login"))
                .isTrue();
    }

    @Test
    void rejectsCatalogWithUnknownAgencyByForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO service_catalog (agency_id, category_id, name, description, created_at)"
                        + " VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                -1L, null, "Fantasma", "N/A"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsCategoryWithUnknownAgencyByForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO service_categories (agency_id, name, created_at)"
                        + " VALUES (?, ?, CURRENT_TIMESTAMP)",
                -1L, "Fantasma"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsRequirementTypeWithUnknownCatalogByForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO requirement_types"
                        + " (service_catalog_id, name, description, estimated_hours, created_at)"
                        + " VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                -1L, "Fantasma", "N/A", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void orphanRemovalDeletesRequirementTypeWhenDetachedFromCatalog() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Orphan"));
        ServiceCatalog catalog = catalogRepository.saveAndFlush(new ServiceCatalog(agency, "Web"));
        catalog.addRequirementType("Landing page", null, null);
        catalog = catalogRepository.saveAndFlush(catalog);
        Long catalogId = catalog.getId();

        catalog.removeRequirementType(catalog.getRequirementTypes().get(0));
        catalogRepository.saveAndFlush(catalog);

        assertThat(requirementTypeRepository.countByServiceCatalogId(catalogId)).isZero();
    }

    /**
     * Deleting a catalog entry cascades to its composed requirement types. This
     * is only allowed while those types are not quoted: once the proposal
     * migration adds quoted_items.requirement_type_id with ON DELETE RESTRICT,
     * deleting a catalog entry already used in a quotation is blocked by the
     * database (documented in the V3 migration).
     */
    @Test
    void deleteCatalogCascadesToUnquotedRequirementTypes() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Cascada"));
        ServiceCatalog catalog = new ServiceCatalog(agency, "Branding");
        catalog.addRequirementType("Logo", null, null);
        catalog.addRequirementType("Brand guide", null, null);
        catalog = catalogRepository.saveAndFlush(catalog);
        Long catalogId = catalog.getId();
        assertThat(requirementTypeRepository.countByServiceCatalogId(catalogId)).isEqualTo(2);

        catalogRepository.delete(catalog);
        catalogRepository.flush();

        assertThat(catalogRepository.findByIdAndAgencyId(catalogId, agency.getId())).isEmpty();
        assertThat(requirementTypeRepository.countByServiceCatalogId(catalogId)).isZero();
    }

    @Test
    void deletingCategoryDetachesItsCatalogEntriesInsteadOfDeletingThem() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Categoria Libre"));
        ServiceCategory category =
                categoryRepository.saveAndFlush(new ServiceCategory(agency, "Marketing"));
        ServiceCatalog catalog = catalogRepository.saveAndFlush(new ServiceCatalog(agency, "SEO audit"));
        catalog.setCategory(category);
        catalogRepository.saveAndFlush(catalog);
        Long catalogId = catalog.getId();

        jdbcTemplate.update("DELETE FROM service_categories WHERE id = ?", category.getId());

        Long remainingCategoryId = jdbcTemplate.queryForObject(
                "SELECT category_id FROM service_catalog WHERE id = ?", Long.class, catalogId);
        assertThat(remainingCategoryId).isNull();
        assertThat(catalogRepository.findById(catalogId)).isPresent();
    }
}
