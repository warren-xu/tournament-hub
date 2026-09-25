package com.warren.warrenament.agent;

import com.warren.warrenament.agent.AgentDtos.AgentView;
import com.warren.warrenament.agent.AgentDtos.CreateAgentRequest;
import com.warren.warrenament.agent.AgentDtos.UpdateAgentRequest;
import com.warren.warrenament.common.Exceptions.BadRequestException;
import com.warren.warrenament.common.Exceptions.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AgentService {

    private final AgentRepository agents;

    public AgentService(AgentRepository agents) {
        this.agents = agents;
    }

    @Transactional(readOnly = true)
    public List<AgentView> findAll(boolean includeRetired) {
        List<Agent> found = includeRetired
                ? agents.findAllByOrderByRoleAscDisplayOrderAscNameAsc()
                : agents.findByActiveTrueOrderByRoleAscDisplayOrderAscNameAsc();
        return found.stream().map(AgentView::of).toList();
    }

    @Transactional
    public AgentView create(CreateAgentRequest request) {
        String name = request.name().trim();
        String role = request.role().trim();
        agents.findByNameIgnoreCase(name).ifPresent(existing -> {
            throw new BadRequestException("There is already an agent called " + existing.getName());
        });
        // New agents land at the end of their role group.
        Agent agent = new Agent(name, role, agents.maxDisplayOrder(role) + 10);
        return AgentView.of(agents.save(agent));
    }

    @Transactional
    public AgentView update(Long id, UpdateAgentRequest request) {
        Agent agent = require(id);
        String name = request.name().trim();

        agents.findByNameIgnoreCase(name).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new BadRequestException("There is already an agent called " + other.getName());
            }
        });

        agent.setName(name);
        agent.setRole(request.role().trim());
        if (request.active() != null) {
            agent.setActive(request.active());
        }
        if (request.displayOrder() != null) {
            agent.setDisplayOrder(request.displayOrder());
        }
        return AgentView.of(agents.save(agent));
    }

    @Transactional
    public void delete(Long id) {
        agents.delete(require(id));
    }

    private Agent require(Long id) {
        return agents.findById(id).orElseThrow(() -> NotFoundException.of("Agent", id));
    }
}
