package com.warren.warrenament.rank;

import java.util.List;

/** Source of the competitive tier list. An interface so sync is testable offline. */
public interface RankCatalog {

    record CatalogRank(int tier, String name, String division, String color, String iconUrl) {
    }

    List<CatalogRank> fetchRanks();
}
