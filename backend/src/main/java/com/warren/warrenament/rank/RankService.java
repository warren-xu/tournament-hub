package com.warren.warrenament.rank;

import com.warren.warrenament.common.Exceptions.NotFoundException;
import com.warren.warrenament.rank.RankDtos.RankView;
import com.warren.warrenament.rank.RankDtos.UpdateRankRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RankService {

    private final RankRepository ranks;

    public RankService(RankRepository ranks) {
        this.ranks = ranks;
    }

    @Transactional(readOnly = true)
    public List<RankView> findAll(boolean includeHidden) {
        List<Rank> found = includeHidden
                ? ranks.findAllByOrderByTierAsc()
                : ranks.findByActiveTrueOrderByTierAsc();
        return found.stream().map(RankView::of).toList();
    }

    @Transactional
    public RankView update(Long id, UpdateRankRequest request) {
        Rank rank = ranks.findById(id)
                .orElseThrow(() -> NotFoundException.of("Rank", id));
        rank.setName(request.name().trim());
        if (request.active() != null) {
            rank.setActive(request.active());
        }
        return RankView.of(ranks.save(rank));
    }
}
