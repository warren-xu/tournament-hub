package com.warren.warrenament.common;

import java.util.List;

/**
 * Outcome of reconciling a local table against an upstream catalog.
 *
 * @param notInSource rows held locally that the source no longer lists. Reported rather
 *                    than deleted: profiles reference this reference data by name, so a
 *                    delete would leave dangling values on player cards.
 * @param note        set when the sync did nothing useful, e.g. an empty response
 */
public record SyncResult(
        int added,
        int updated,
        int unchanged,
        List<String> notInSource,
        String note
) {
    public static SyncResult noop(String note) {
        return new SyncResult(0, 0, 0, List.of(), note);
    }
}
