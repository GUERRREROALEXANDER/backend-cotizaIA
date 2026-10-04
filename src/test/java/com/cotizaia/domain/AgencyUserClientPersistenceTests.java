package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.AppUserRepository;
import com.cotizaia.repository.ClientRepository;
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
class AgencyUserClientPersistenceTests {

    @Autowired
    private AgencyRepository agencyRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsAgencyOwnerStaffAndClients() {
        Agency agency = new Agency("Estudio Creativo");
        agency.addUser(new AppUser(agency, "Ana Duena", "ana@estudio.co", UserRole.OWNER));
        agency.addUser(new AppUser(agency, "Luis Colaborador", "luis@estudio.co", UserRole.STAFF));

        Client client = new Client(agency, "Restaurante El Sabor", "contacto@elsabor.co");
        client.setCompany("El Sabor S.A.S.");
        agency.addClient(client);

        Agency saved = agencyRepository.saveAndFlush(agency);
        Long agencyId = saved.getId();

        assertThat(agencyId).isNotNull();
        assertThat(appUserRepository.findByAgencyIdOrderByIdAsc(agencyId))
                .extracting(AppUser::getEmail)
                .containsExactly("ana@estudio.co", "luis@estudio.co");
        assertThat(appUserRepository.findByAgencyIdAndRole(agencyId, UserRole.OWNER))
                .isPresent();
        assertThat(appUserRepository.existsByAgencyIdAndRole(agencyId, UserRole.OWNER)).isTrue();
        assertThat(clientRepository.findByAgencyIdOrderByIdAsc(agencyId))
                .extracting(Client::getName)
                .containsExactly("Restaurante El Sabor");
    }

    @Test
    void persistsClientsAndUsersOnTheirOwnKeepingForeignKey() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Dos"));

        AppUser owner = appUserRepository.saveAndFlush(
                new AppUser(agency, "Marta Owner", "marta@dos.co", UserRole.OWNER));
        Client client = clientRepository.saveAndFlush(
                new Client(agency, "Panaderia Central", "hola@central.co"));

        assertThat(owner.getAgency().getId()).isEqualTo(agency.getId());
        assertThat(client.getAgency().getId()).isEqualTo(agency.getId());
        assertThat(clientRepository.findByAgencyIdOrderByIdAsc(agency.getId())).hasSize(1);
    }

    @Test
    void rejectsSecondOwnerForSameAgency() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Tres"));
        appUserRepository.saveAndFlush(
                new AppUser(agency, "Owner Uno", "uno@tres.co", UserRole.OWNER));

        AppUser secondOwner = new AppUser(agency, "Owner Dos", "dos@tres.co", UserRole.OWNER);

        assertThatThrownBy(() -> appUserRepository.saveAndFlush(secondOwner))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsManyStaffButOnlyOneOwnerPerAgency() {
        Agency agency = agencyRepository.saveAndFlush(new Agency("Agencia Cuatro"));
        appUserRepository.saveAndFlush(new AppUser(agency, "Owner", "owner@cuatro.co", UserRole.OWNER));
        appUserRepository.saveAndFlush(new AppUser(agency, "Staff A", "a@cuatro.co", UserRole.STAFF));
        appUserRepository.saveAndFlush(new AppUser(agency, "Staff B", "b@cuatro.co", UserRole.STAFF));

        assertThat(appUserRepository.findByAgencyIdOrderByIdAsc(agency.getId())).hasSize(3);
    }

    @Test
    void rejectsUserWithUnknownAgencyByForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (agency_id, full_name, email, role, owner_flag, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                -1L, "Fantasma", "fantasma@nowhere.co", "STAFF", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsClientWithUnknownAgencyByForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO clients (agency_id, name, email, company, created_at)"
                        + " VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                -1L, "Cliente Fantasma", "fantasma@nowhere.co", "N/A"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
