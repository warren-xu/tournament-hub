package com.warren.warrenament.playercard;

import java.util.List;

public interface PlayerCardCatalog {
    record CatalogCard(String externalId, String name, String smallArt, String largeArt) {}
    List<CatalogCard> fetchCards();
}
