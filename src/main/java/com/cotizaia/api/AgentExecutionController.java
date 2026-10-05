package com.cotizaia.api;

import com.cotizaia.domain.AgentExecution;
import com.cotizaia.service.AgentExecutionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Run-log API (project.txt section 2: "Ver el pipeline del agente ejecutarse en
 * vivo"). GET by id returns the execution with its steps already ordered, which
 * is exactly what the live view polls.
 */
@RestController
@RequestMapping("/api/executions")
public class AgentExecutionController {

    private final AgentExecutionService agentExecutionService;

    public AgentExecutionController(AgentExecutionService agentExecutionService) {
        this.agentExecutionService = agentExecutionService;
    }

    @GetMapping("/{id}")
    public AgentExecutionResponse get(@PathVariable Long id, CurrentUser user) {
        AgentExecution execution = agentExecutionService.get(user.agencyId(), id);
        List<AgentStepResponse> steps = agentExecutionService.timeline(id).stream()
                .map(AgentStepResponse::from)
                .toList();
        return AgentExecutionResponse.from(execution, steps);
    }
}
