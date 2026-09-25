package com.warren.warrenament.playercard;

import com.warren.warrenament.common.SyncResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/player-cards")
public class PlayerCardController {
    private final PlayerCardService service;

    public PlayerCardController(PlayerCardService service) {
        this.service = service;
    }

    @GetMapping
    public List<PlayerCardView> list() {
        return service.findAll();
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public SyncResult sync() {
        return service.sync();
    }
}
