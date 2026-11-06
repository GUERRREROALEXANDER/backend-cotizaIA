package com.cotizaia.api;

import com.cotizaia.service.AgentExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Run-log API (project.txt section 2: "Watch the agent pipeline run
 * live"). GET by id returns the execution with its steps already ordered, which
 * is exactly what the live view polls.
 */
@RestController
@RequestMapping("/api/executions")
@Tag(name = "Executions")
public class AgentExecutionController {

    private final AgentExecutionService agentExecutionService;

    public AgentExecutionController(AgentExecutionService agentExecutionService) {
        this.agentExecutionService = agentExecutionService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an execution timeline")
    public AgentExecutionResponse get(@PathVariable Long id, CurrentUser user) {
        return AgentExecutionResponse.from(agentExecutionService.detail(user.agencyId(), id));
    }

    @GetMapping
    @Operation(summary = "List executions for a brief, or the caller's latest runs when no brief is given")
    public List<AgentExecutionResponse> list(@RequestParam(required = false) @Positive Long briefId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit, CurrentUser user) {
        if (briefId == null) {
            return agentExecutionService.listLatest(user.agencyId(), limit).stream()
                    .map(AgentExecutionResponse::from).toList();
        }
        return agentExecutionService.listByBrief(user.agencyId(), briefId).stream()
                .map(AgentExecutionResponse::from).toList();
    }
}
