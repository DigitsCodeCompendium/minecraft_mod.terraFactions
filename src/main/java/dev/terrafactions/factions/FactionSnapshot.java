package dev.terrafactions.factions;

import dev.terrafactions.territory.TerritoryType;

import java.util.List;
import java.util.UUID;

public record FactionSnapshot(
        UUID id,
        String name,
        String description,
        int color,
        List<ClaimSnapshot> claims,
        CapitalSnapshot capital) {

    public record ClaimSnapshot(int x, int z, String dimension, TerritoryType type) {
    }

    public record CapitalSnapshot(int x, int z, String dimension) {
    }
}
