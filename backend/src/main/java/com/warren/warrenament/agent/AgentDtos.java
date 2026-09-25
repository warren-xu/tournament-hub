package com.warren.warrenament.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AgentDtos {

    private AgentDtos() {
    }

    public record AgentView(
            Long id,
            String name,
            String role,
            boolean active,
            int displayOrder,
            String iconUrl,
            String portraitUrl,
            float portraitFocusX
    ) {
        public static AgentView of(Agent a) {
            return new AgentView(a.getId(), a.getName(), a.getRole(), a.isActive(),
                    a.getDisplayOrder(), a.getIconUrl(), a.getPortraitUrl(),
                    a.getPortraitFocusX());
        }
    }


    public record CreateAgentRequest(
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = 32) String role
    ) {
    }

    public record UpdateAgentRequest(
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = 32) String role,
            Boolean active,
            Integer displayOrder
    ) {
    }
}
