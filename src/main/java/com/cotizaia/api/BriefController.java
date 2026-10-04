package com.cotizaia.api;

import com.cotizaia.domain.Brief;
import com.cotizaia.service.BriefService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Brief ingest API. {@code POST /api/briefs} accepts any supported channel;
 * channel-specific parsing happens behind the {@code BriefSource} port.
 */
@RestController
@RequestMapping("/api/briefs")
public class BriefController {

    private final BriefService briefService;

    public BriefController(BriefService briefService) {
        this.briefService = briefService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BriefResponse ingest(@Valid @RequestBody BriefIngestRequest request) {
        Brief brief = briefService.ingest(request.source(), request.clientId(), request.payload());
        return BriefResponse.from(brief);
    }

    @GetMapping("/{id}")
    public BriefResponse get(@PathVariable Long id) {
        return BriefResponse.from(briefService.get(id));
    }
}
