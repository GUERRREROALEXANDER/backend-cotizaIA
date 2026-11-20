package com.cotizaia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotizaia.domain.Agency;
import com.cotizaia.pricing.PricingModel;
import com.cotizaia.repository.AgencyRepository;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgencyServiceTests {

    private final AgencyRepository agencies = mock(AgencyRepository.class);
    private final AgencyService service = new AgencyService(agencies);

    @Test
    void readsTheAgencyPricingModel() {
        Agency agency = new Agency("Estudio Nariño Digital");
        agency.setPricingModel(PricingModel.PHASED);
        when(agencies.findById(1L)).thenReturn(Optional.of(agency));

        assertThat(service.getPricingModel(1L)).isEqualTo(PricingModel.PHASED);
    }

    @Test
    void updatingTheModelChangesTheAgencyAndReturnsTheNewValue() {
        Agency agency = new Agency("Estudio Nariño Digital");
        when(agencies.findById(1L)).thenReturn(Optional.of(agency));

        assertThat(service.updatePricingModel(1L, PricingModel.HOURLY)).isEqualTo(PricingModel.HOURLY);
        assertThat(agency.getPricingModel()).isEqualTo(PricingModel.HOURLY);
    }

    @Test
    void anUnknownAgencyIsNotFound() {
        when(agencies.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPricingModel(9L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("9");
    }
}
