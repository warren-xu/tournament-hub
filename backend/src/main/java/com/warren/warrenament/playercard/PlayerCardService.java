package com.warren.warrenament.playercard;

import com.warren.warrenament.common.SyncResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Service
public class PlayerCardService {
    private final PlayerCardRepository cards;
    private final PlayerCardCatalog catalog;

    public PlayerCardService(PlayerCardRepository cards, PlayerCardCatalog catalog) {
        this.cards = cards;
        this.catalog = catalog;
    }

    // Reads are database-only. The external catalog is used exclusively by sync().
    @Transactional(readOnly = true)
    public List<PlayerCardView> findAll() {
        return cards.findAllByOrderByNameAscIdAsc().stream().map(PlayerCardView::of).toList();
    }

    @Transactional
    public SyncResult sync() {
        var incoming = catalog.fetchCards().stream()
                .filter(c -> c.largeArt() != null && !c.largeArt().isBlank())
                .toList();
        if (incoming.isEmpty()) return SyncResult.noop("The source returned no player cards with portrait artwork.");

        var existing = new HashMap<String, PlayerCard>();
        cards.findAll().forEach(c -> existing.put(c.getExternalId(), c));
        var seen = new HashSet<String>();
        int added = 0, updated = 0, unchanged = 0;
        for (var source : incoming) {
            if (!seen.add(source.externalId())) continue;
            var card = existing.get(source.externalId());
            boolean fresh = card == null;
            if (fresh) {
                card = new PlayerCard();
                card.setExternalId(source.externalId());
            } else if (Objects.equals(card.getName(), source.name())
                    && Objects.equals(card.getSmallArt(), source.smallArt())
                    && Objects.equals(card.getLargeArt(), source.largeArt())) {
                unchanged++;
                continue;
            }
            card.setName(source.name());
            card.setSmallArt(source.smallArt());
            card.setLargeArt(source.largeArt());
            cards.save(card);
            if (fresh) added++; else updated++;
        }
        // Never delete missing cards: existing profile selections remain valid.
        var missing = existing.values().stream().filter(c -> !seen.contains(c.getExternalId()))
                .map(PlayerCard::getName).sorted().toList();
        return new SyncResult(added, updated, unchanged, missing, null);
    }
}
