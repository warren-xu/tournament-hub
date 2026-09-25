package com.warren.warrenament.agent;

import com.warren.warrenament.agent.AgentDtos.AgentView;
import com.warren.warrenament.common.SyncResult;
import com.warren.warrenament.agent.AgentDtos.CreateAgentRequest;
import com.warren.warrenament.agent.AgentDtos.UpdateAgentRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService service;
    private final AgentSyncService syncService;

    public AgentController(AgentService service, AgentSyncService syncService) {
        this.service = service;
        this.syncService = syncService;
    }

    /** Public: the profile picker reads this. Retired agents are hidden by default. */
    @GetMapping
    public List<AgentView> list(
            @RequestParam(defaultValue = "false") boolean includeRetired) {
        return service.findAll(includeRetired);
    }

    /**
     * Pulls the roster from the upstream source and reconciles it. Adds and updates
     * only — nothing is deleted, and locally retired agents stay retired.
     */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public SyncResult sync() {
        return syncService.sync();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AgentView create(@Valid @RequestBody CreateAgentRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AgentView update(@PathVariable Long id,
                            @Valid @RequestBody UpdateAgentRequest request) {
        return service.update(id, request);
    }

    /**
     * Hard delete. Prefer setting {@code active=false} instead: profiles store the agent
     * name rather than a foreign key, so a deleted agent lingers on player cards.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
