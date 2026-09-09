package dev.terrafactions.territory;

import java.util.UUID;

public record TerritoryClaim(TerritoryKey key, UUID factionId, TerritoryType type) {
}
