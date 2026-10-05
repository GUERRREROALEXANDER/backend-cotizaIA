package com.cotizaia.api;

import com.cotizaia.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes agency-scoped clients operations (project.txt section 2).
 * Application services own persistence and rules so HTTP mapping stays independent of the domain workflow.
 */
@RestController
@RequestMapping("/api/clients")
@Tag(name = "Clients")
public class ClientController {

    private final ClientService clients;

    public ClientController(ClientService clients) {
        this.clients = clients;
    }

    @GetMapping
    @Operation(summary = "List clients")
    public List<ClientResponse> list(CurrentUser user) {
        return clients.list(user.agencyId()).stream().map(ClientResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get client")
    public ClientResponse get(@PathVariable Long id, CurrentUser user) {
        return ClientResponse.from(clients.get(user.agencyId(), id));
    }

    @PostMapping
    @Operation(summary = "Create client")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse create(@Valid @RequestBody ClientRequest request, CurrentUser user) {
        return ClientResponse.from(clients.create(user.agencyId(), request.name(), request.email(), request.company()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update client")
    public ClientResponse update(@PathVariable Long id, @Valid @RequestBody ClientRequest request,
            CurrentUser user) {
        return ClientResponse.from(clients.update(user.agencyId(), id,
                request.name(), request.email(), request.company()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete client")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, CurrentUser user) {
        clients.delete(user.agencyId(), id);
    }
}
