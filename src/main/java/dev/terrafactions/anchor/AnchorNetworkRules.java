package dev.terrafactions.anchor;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AnchorNetworkRules {
    public enum LinkType {
        NONE,
        MUTUAL,
        ARROW_TO_FIRST,
        ARROW_TO_SECOND
    }

    private AnchorNetworkRules() {
    }

    public static long requiredPowerTenths(AnchorTier tier, int projectedClaims) {
        return (long) Math.max(0, projectedClaims) * tier.powerTenthsPerClaim();
    }

    public static LinkType linkType(AnchorMapSnapshot first, AnchorMapSnapshot second) {
        if (!first.factionId().equals(second.factionId())
                || !first.dimension().equals(second.dimension())) {
            return LinkType.NONE;
        }
        int firstChunkX = Math.floorDiv(first.x(), 16);
        int firstChunkZ = Math.floorDiv(first.z(), 16);
        int secondChunkX = Math.floorDiv(second.x(), 16);
        int secondChunkZ = Math.floorDiv(second.z(), 16);
        long dx = (long) firstChunkX - secondChunkX;
        long dz = (long) firstChunkZ - secondChunkZ;
        long distanceSquared = dx * dx + dz * dz;
        boolean firstReachesSecond = first.projectedRadius() > 0
                && distanceSquared <= (long) first.projectedRadius() * first.projectedRadius();
        boolean secondReachesFirst = second.projectedRadius() > 0
                && distanceSquared <= (long) second.projectedRadius() * second.projectedRadius();
        if (firstReachesSecond && secondReachesFirst) return LinkType.MUTUAL;
        if (firstReachesSecond) return LinkType.ARROW_TO_SECOND;
        if (secondReachesFirst) return LinkType.ARROW_TO_FIRST;
        return LinkType.NONE;
    }

    public static Set<String> connectedToCapital(List<AnchorMapSnapshot> anchors,
                                                  Set<String> capitalAnchors,
                                                  Set<String> territoriallyReachable) {
        Set<String> connected = new HashSet<>();
        ArrayDeque<AnchorMapSnapshot> pending = new ArrayDeque<>();
        for (AnchorMapSnapshot anchor : anchors) {
            if (capitalAnchors.contains(anchor.id()) && territoriallyReachable.contains(anchor.id())) {
                connected.add(anchor.id());
                pending.add(anchor);
            }
        }
        while (!pending.isEmpty()) {
            AnchorMapSnapshot source = pending.removeFirst();
            for (AnchorMapSnapshot candidate : anchors) {
                if (connected.contains(candidate.id()) || !territoriallyReachable.contains(candidate.id())) {
                    continue;
                }
                // Radius links carry connectivity both ways. The map arrow only identifies the
                // smaller one-way footprint; it is not a supply-flow direction.
                if (linkType(source, candidate) != LinkType.NONE) {
                    connected.add(candidate.id());
                    pending.addLast(candidate);
                }
            }
        }
        return Set.copyOf(connected);
    }

    public static AnchorPowerState powerState(long requiredTenths, long usableTenths) {
        if (requiredTenths <= 0 || usableTenths >= requiredTenths) return AnchorPowerState.FULL;
        return usableTenths <= 0 ? AnchorPowerState.UNPOWERED : AnchorPowerState.UNDERPOWERED;
    }

    public static AnchorVulnerabilityState vulnerabilityState(AnchorPowerState powerState,
                                                               boolean connected, boolean hasClaims,
                                                               long isolationStart, long now, long graceTicks) {
        if (hasClaims && powerState != AnchorPowerState.FULL) return AnchorVulnerabilityState.VULNERABLE;
        if (connected) return AnchorVulnerabilityState.PROTECTED;
        return now - isolationStart >= graceTicks
                ? AnchorVulnerabilityState.VULNERABLE : AnchorVulnerabilityState.GRACE_PERIOD;
    }
}
