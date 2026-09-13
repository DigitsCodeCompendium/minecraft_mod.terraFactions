package dev.terrafactions.territory;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Set;

public final class TerritoryRules {
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private TerritoryRules() {
    }

    public static boolean touches(Collection<TerritoryKey> territory, TerritoryKey target) {
        Set<TerritoryKey> keys = territory instanceof Set<TerritoryKey> set ? set : new HashSet<>(territory);
        for (int[] direction : DIRECTIONS) {
            if (keys.contains(target.offset(direction[0], direction[1]))) {
                return true;
            }
        }
        return false;
    }

    public static boolean remainsConnected(Collection<TerritoryKey> territory, TerritoryKey removed) {
        Set<TerritoryKey> remaining = new HashSet<>(territory);
        remaining.remove(removed);
        return isConnected(remaining);
    }

    public static boolean isConnected(Collection<TerritoryKey> territory) {
        Set<TerritoryKey> remaining = territory instanceof Set<TerritoryKey> set
                ? set : new HashSet<>(territory);
        if (remaining.size() < 2) {
            return true;
        }

        TerritoryKey first = remaining.iterator().next();
        Set<TerritoryKey> visited = new HashSet<>();
        ArrayDeque<TerritoryKey> queue = new ArrayDeque<>();
        queue.add(first);
        while (!queue.isEmpty()) {
            TerritoryKey key = queue.removeFirst();
            if (!remaining.contains(key) || !visited.add(key)) {
                continue;
            }
            for (int[] direction : DIRECTIONS) {
                queue.addLast(key.offset(direction[0], direction[1]));
            }
        }
        return visited.size() == remaining.size();
    }

    public static Set<TerritoryKey> connectedComponent(Collection<TerritoryKey> territory, TerritoryKey start) {
        Set<TerritoryKey> remaining = territory instanceof Set<TerritoryKey> set
                ? set : new HashSet<>(territory);
        if (!remaining.contains(start)) return Set.of();
        Set<TerritoryKey> visited = new HashSet<>();
        ArrayDeque<TerritoryKey> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            TerritoryKey key = queue.removeFirst();
            if (!remaining.contains(key) || !visited.add(key)) continue;
            for (int[] direction : DIRECTIONS) queue.addLast(key.offset(direction[0], direction[1]));
        }
        return visited;
    }

    /** A centered square extending the requested chunk radius in every cardinal direction. */
    public static Set<TerritoryKey> centeredSquare(TerritoryKey center, int radius) {
        Set<TerritoryKey> result = new LinkedHashSet<>();
        radius = Math.max(0, radius);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                result.add(center.offset(x, z));
            }
        }
        return result;
    }

    /** Finds the largest complete discrete circle whose claim count fits the supplied budget. */
    public static CircularProjection largestCircularProjection(TerritoryKey center, int maxClaims) {
        if (maxClaims <= 0) return new CircularProjection(0, Set.of());
        int radius = Math.max(0, (int) Math.floor(Math.sqrt(maxClaims / Math.PI)));
        Set<TerritoryKey> claims = circularProjection(center, radius);
        while (claims.size() > maxClaims && radius > 0) {
            claims = circularProjection(center, --radius);
        }
        while (true) {
            Set<TerritoryKey> next = circularProjection(center, radius + 1);
            if (next.size() > maxClaims) return new CircularProjection(radius, claims);
            radius++;
            claims = next;
        }
    }

    public static Set<TerritoryKey> circularProjection(TerritoryKey center, int radius) {
        Set<TerritoryKey> result = new LinkedHashSet<>();
        long radiusSquared = (long) radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx != 0 || dz != 0) && (long) dx * dx + (long) dz * dz <= radiusSquared) {
                    result.add(center.offset(dx, dz));
                }
            }
        }
        return result;
    }

    public record CircularProjection(int radius, Set<TerritoryKey> claims) {
        public CircularProjection {
            claims = Set.copyOf(claims);
        }
    }
}
