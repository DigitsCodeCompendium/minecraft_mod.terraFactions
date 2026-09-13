package dev.terrafactions.territory;

import java.util.UUID;

public record TerritoryClaim(TerritoryKey key, UUID factionId, TerritoryType type, boolean projected) {
    public TerritoryClaim(TerritoryKey key, UUID factionId, TerritoryType type) {
        this(key, factionId, type, false);
    }

    public long powerCostTenths() {
        // Projected claims are only the materialized union of anchor circles. Anchor allocation is
        // charged separately so overlaps and obstructed chunks still cost each anchor their full footprint.
        return projected ? 0L : type.cost() * 10L;
    }
}
