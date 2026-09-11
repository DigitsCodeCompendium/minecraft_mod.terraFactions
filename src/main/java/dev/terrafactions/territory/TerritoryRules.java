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
}
