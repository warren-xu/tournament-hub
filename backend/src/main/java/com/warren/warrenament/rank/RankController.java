package com.warren.warrenament.rank;

import com.warren.warrenament.common.SyncResult;
import com.warren.warrenament.rank.RankDtos.RankView;
import com.warren.warrenament.rank.RankDtos.UpdateRankRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/ranks")
public class RankController {

    private final RankService service;
    private final RankSyncService syncService;

    public RankController(RankService service, RankSyncService syncService) {
        this.service = service;
        this.syncService = syncService;
    }

    /** Public: the profile form and player cards read this. */
    @GetMapping
    public List<RankView> list(@RequestParam(defaultValue = "false") boolean includeHidden) {
        return service.findAll(includeHidden);
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public SyncResult sync() {
        return syncService.sync();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RankView update(@PathVariable Long id,
                           @Valid @RequestBody UpdateRankRequest request) {
        return service.update(id, request);
    }
}
