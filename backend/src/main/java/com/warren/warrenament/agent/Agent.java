package com.warren.warrenament.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "agents")
@Getter
@Setter
@NoArgsConstructor
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String role;

    /** Retired agents stay in the table so existing profiles keep rendering. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** Riot's agent uuid, from the sync source. Survives a rename; the name does not. */
    @Column(name = "external_id", unique = true)
    private String externalId;

    @Column(name = "icon_url")
    private String iconUrl;

    /** Full-body art, used as the overlay on a player card. */
    @Column(name = "portrait_url")
    private String portraitUrl;

    /**
     * Where this agent's body actually sits across the portrait, 0..1. Riot does not
     * centre them on the shared canvas, so the card offsets each one by its own amount.
     * Measured locally and never touched by the sync.
     */
    @Column(name = "portrait_focus_x", nullable = false)
    private float portraitFocusX = 0.5f;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public Agent(String name, String role, int displayOrder) {
        this.name = name;
        this.role = role;
        this.displayOrder = displayOrder;
    }
}
