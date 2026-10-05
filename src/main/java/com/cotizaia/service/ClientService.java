package com.cotizaia.service;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Client;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.ClientRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns client maintenance within an agency (project.txt section 2).
 * Scoped lookups protect tenant data and database constraints retain referenced clients.
 */
@Service
@Transactional
public class ClientService {

    private final ClientRepository clients;

    private final AgencyRepository agencies;

    public ClientService(ClientRepository clients, AgencyRepository agencies) {
        this.clients = clients;
        this.agencies = agencies;
    }

    @Transactional(readOnly = true)
    public List<Client> list(Long agencyId) {
        return clients.findByAgencyIdOrderByIdAsc(agencyId);
    }

    @Transactional(readOnly = true)
    public Client get(Long agencyId, Long id) {
        return clients.findByIdAndAgencyId(id, agencyId)
                .orElseThrow(() -> new NoSuchElementException("Client not found: " + id));
    }

    public Client create(Long agencyId, String name, String email, String company) {
        Agency agency = agencies.findById(agencyId)
                .orElseThrow(() -> new NoSuchElementException("Agency not found: " + agencyId));
        Client client = new Client(agency, name, email);
        client.setCompany(company);
        return clients.saveAndFlush(client);
    }

    public Client update(Long agencyId, Long id, String name, String email, String company) {
        Client client = get(agencyId, id);
        client.setName(name);
        client.setEmail(email);
        client.setCompany(company);
        return clients.saveAndFlush(client);
    }

    public void delete(Long agencyId, Long id) {
        clients.delete(get(agencyId, id));
        clients.flush();
    }
}
