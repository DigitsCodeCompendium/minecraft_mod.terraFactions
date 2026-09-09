package dev.terrafactions.territory;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryRulesTest {
    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void cardinalNeighborsConnectButDiagonalsDoNot() {
        Set<TerritoryKey> territory = Set.of(key(0, 0));

        assertTrue(TerritoryRules.touches(territory, key(1, 0)));
        assertTrue(TerritoryRules.touches(territory, key(0, -1)));
        assertFalse(TerritoryRules.touches(territory, key(1, 1)));
    }

    @Test
    void claimsInAnotherDimensionDoNotConnect() {
        Set<TerritoryKey> territory = Set.of(key(0, 0));

        assertFalse(TerritoryRules.touches(territory, new TerritoryKey("minecraft:the_nether", 1, 0)));
    }

    @Test
    void removingBridgeWouldSplitTerritory() {
        Set<TerritoryKey> territory = Set.of(key(0, 0), key(1, 0), key(2, 0));

        assertFalse(TerritoryRules.remainsConnected(territory, key(1, 0)));
        assertTrue(TerritoryRules.remainsConnected(territory, key(0, 0)));
    }

    @Test
    void removingFromRingRemainsConnected() {
        Set<TerritoryKey> territory = Set.of(
                key(0, 0), key(1, 0), key(2, 0), key(2, 1),
                key(2, 2), key(1, 2), key(0, 2), key(0, 1));

        assertTrue(TerritoryRules.remainsConnected(territory, key(1, 0)));
    }

    @Test
    void centeredBulkSizesMatchFactionCommandSemantics() {
        assertEquals(Set.of(key(0, 0)), TerritoryRules.centeredSquare(key(0, 0), 1));
        assertEquals(9, TerritoryRules.centeredSquare(key(0, 0), 2).size());
        assertTrue(TerritoryRules.centeredSquare(key(0, 0), 2).contains(key(-1, 1)));
    }

    @Test
    void connectivityCheckRejectsSeparatedGroups() {
        assertTrue(TerritoryRules.isConnected(Set.of(key(0, 0), key(1, 0), key(1, 1))));
        assertFalse(TerritoryRules.isConnected(Set.of(key(0, 0), key(2, 0))));
    }

    private static TerritoryKey key(int x, int z) {
        return new TerritoryKey(OVERWORLD, x, z);
    }
}
