package dev.terrafactions.territory;

import net.minecraft.resources.ResourceLocation;

public record TerritoryKey(String dimension, int x, int z) {
    public static TerritoryKey of(ResourceLocation dimension, int x, int z) {
        return new TerritoryKey(dimension.toString(), x, z);
    }

    public TerritoryKey offset(int dx, int dz) {
        return new TerritoryKey(dimension, x + dx, z + dz);
    }
}
