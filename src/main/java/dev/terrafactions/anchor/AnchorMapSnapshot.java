package dev.terrafactions.anchor;

import java.util.UUID;

public record AnchorMapSnapshot(String id, UUID factionId, String dimension, int x, int y, int z,
                                AnchorTier tier, int allocatedPower, int usablePowerTenths, int priority,
                                int projectedRadius, int projectedClaims,
                                AnchorPowerState powerState, AnchorConnectionState connectionState,
                                AnchorVulnerabilityState vulnerabilityState, long isolationStartTick) {
}
